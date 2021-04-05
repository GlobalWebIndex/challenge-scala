package gwi

import akka._
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.http.scaladsl.model.Uri
import akka.pattern.StatusReply
import akka.pattern.StatusReply._
import org.slf4j.LoggerFactory
import scala.collection.immutable.Queue

object Manager {
  val MaxWorkers = 2

  sealed trait Command

  final case class Create(source: Uri, replyTo: ActorRef[StatusReply[TaskId]]) extends Command
  final case class GetAll(replyTo: ActorRef[StatusReply[List[Task]]]) extends Command
  final case class Get(id: TaskId, replyTo: ActorRef[StatusReply[Task]]) extends Command
  final case class Cancel(id: TaskId, replyTo: ActorRef[StatusReply[Done]]) extends Command

  private final case class WorkerResponse(response: Worker.Response) extends Command
  private final case class WorkerTerminated(id: TaskId) extends Command

  def apply(factory: Worker.Factory): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      val respondTo = context.messageAdapter[Worker.Response](WorkerResponse)
      apply(factory, respondTo)
    }

  private val log = LoggerFactory.getLogger(classOf[Manager.type])

  private def apply(factory: Worker.Factory, respondTo: ActorRef[Worker.Response]): Behavior[Command] = {
    // latestId = id of the latest task, used to generate a new id
    // tasks = all tasks seen by the manager
    // queue = ids of scheduled tasks
    // workers = actor refs of currently running workers
    def manager(
        latestId: TaskId,
        tasks: Map[TaskId, Task],
        queue: Queue[TaskId],
        workers: Map[TaskId, ActorRef[Worker.Command]]
    ): Behavior[Command] = Behaviors.receive[Command] {
      case (context, Create(source, replyTo)) if queue.isEmpty && workers.size < MaxWorkers =>
        log.debug("Creating and running a new task processing {}", source)
        val task = RunningTask(latestId + 1, source, None)
        val worker = spawnWorker(context, task)
        replyTo ! success(task.id)
        manager(task.id, tasks.updated(task.id, task), queue, workers.updated(task.id, worker))

      case (_, Create(source, replyTo)) =>
        log.debug("Creating and enqueueing a new task processing {}", source)
        val task = ScheduledTask(latestId + 1, source)
        replyTo ! success(task.id)
        manager(task.id, tasks.updated(task.id, task), queue.enqueue(task.id), workers)

      case (_, GetAll(replyTo)) =>
        log.debug("Getting all task")
        replyTo ! success(tasks.values.toList.sortBy(_.id.value))
        Behaviors.same

      case (_, Get(id, replyTo)) =>
        log.debug("Getting task {}", id.value)
        tasks.get(id) match {
          case None       => replyTo ! error(BadRequestError("Task not found"))
          case Some(task) => replyTo ! success(task)
        }
        Behaviors.same

      case (_, Cancel(id, replyTo)) =>
        log.debug("Cancelling task {}", id.value)
        tasks.get(id) match {
          case None =>
            log.error("Task {} not found", id.value)
            replyTo ! error(BadRequestError("Task not found"))
            Behaviors.same

          case Some(task: ScheduledTask) =>
            replyTo ! ack()
            manager(latestId, tasks.updated(id, task.cancel), queue.filterNot(_ == id), workers)

          case Some(task: RunningTask) =>
            replyTo ! ack()
            workers.get(id).foreach(_ ! Worker.Cancel)
            manager(latestId, tasks.updated(id, task.cancel), queue, workers)

          case Some(_: DoneTask | _: FailedTask | _: CanceledTask) =>
            log.error("Task {} cannot be terminated", id.value)
            replyTo ! error(BadRequestError("Cannot cancel a terminated task"))
            Behaviors.same
        }

      case (_, WorkerResponse(Worker.ReportStats(id, stats))) =>
        tasks.get(id) match {
          case Some(task: RunningTask) =>
            log.debug("Got stats {} for task {}", stats, id.value)
            manager(latestId, tasks.updated(id, task.stats(stats)), queue, workers)
          case _ =>
            Behaviors.same
        }

      case (_, WorkerResponse(Worker.WorkerFinished(id, stats))) =>
        log.debug("Worker for task {} finished", id.value)
        tasks.get(id) match {
          case Some(task: RunningTask) =>
            manager(latestId, tasks.updated(id, task.done(resultUri(id), stats)), queue, workers)
          case Some(_: CanceledTask) =>
            manager(latestId, tasks, queue, workers)
          case _ =>
            // This should never happen™️. If it still happens, stop the manager.
            log.error("Task {} is not running, cannot be terminated", id.value)
            Behaviors.stopped
        }

      case (_, WorkerResponse(Worker.WorkerFailed(id, cause))) =>
        log.debug("Worker for task {} failed", id.value)
        tasks.get(id) match {
          case Some(task: RunningTask) =>
            manager(latestId, tasks.updated(id, task.fail(cause)), queue, workers)
          case Some(_: CanceledTask) =>
            manager(latestId, tasks, queue, workers)
          case _ =>
            // This should never happen™️. If it still happens, stop the manager.
            log.error("Task {} is not running, cannot be terminated", id.value)
            Behaviors.stopped
        }

      case (context, WorkerTerminated(terminatedId)) =>
        log.debug("Worker for task {} terminated", terminatedId.value)
        val fixedTasks = tasks.get(terminatedId) match {
          case Some(task: RunningTask) => tasks.updated(terminatedId, task.fail(new IllegalStateException))
          case _                       => tasks
        }

        val fixedWorkers = workers.removed(terminatedId)

        if (queue.nonEmpty && workers.size <= MaxWorkers) {
          val (id, newQueue) = queue.dequeue
          fixedTasks.get(id) match {
            case Some(task: ScheduledTask) =>
              log.debug("Scheduling task {}", id.value)
              val runningTask = task.run
              val worker = spawnWorker(context, runningTask)
              manager(latestId, fixedTasks.updated(id, runningTask), newQueue, fixedWorkers.updated(id, worker))

            case _ =>
              // This should never happen™️. If it still happens, stop the manager.
              log.error("Enqueued task {} cannot be run", id.value)
              Behaviors.stopped
          }
        } else manager(latestId, fixedTasks, queue, fixedWorkers)
    }

    def spawnWorker(context: ActorContext[Command], task: RunningTask): ActorRef[Worker.Command] = {
      val worker = context.spawn(factory(task, respondTo), name = s"task-${task.id.value}")
      context.watchWith(worker, WorkerTerminated(task.id))
      worker
    }

    def resultUri(id: TaskId): Uri = Uri(s"http://localhost:8080/result/${id.value}")

    manager(TaskId(0), Map.empty, Queue.empty, Map.empty)
  }
}
