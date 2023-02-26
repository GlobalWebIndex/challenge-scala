package cz.vlasec.gwi.csvimport.service

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import CsvWorker._
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.util.Timeout
import cz.vlasec.gwi.csvimport.service.CsvTask.TaskCommand

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/**
 * Task service receives CSV URLs to process, creates tasks, monitors their status and cancels them on demand.
 * Idle workers need to be supplied for the service to be able to process anything.
 * Task service's behavior depends on its task queue and idle workers count.
 * Task service only spawns actors of type Task as children, so it can list the tasks easily from its actor context.
 */
object CsvService {
  sealed trait ServiceCommand
  final case class EnqueueTask(csv: CsvUrl, replyTo: ActorRef[EnqueueTaskResponse]) extends ServiceCommand
  final case class TaskStatus(taskId: TaskId, replyTo: ActorRef[Either[StatusFailure, TaskStatusReport]]) extends ServiceCommand
  final case class ListTasks(replyTo: ActorRef[Seq[TaskStatusReport]]) extends ServiceCommand
  final case class CancelTask(taskId: TaskId) extends ServiceCommand
  // Idle workers are pushed here rather than pulled on demand, to remove the need for
  private[service] final case class IdleWorker(taskId: ActorRef[WorkerCommand]) extends ServiceCommand

  private case class CsvServiceState(nextTaskId: TaskId, taskQueue: Vector[TaskRef], idleWorkers: Set[WorkerRef]) {
    def workerOption: (Option[WorkerRef], Set[WorkerRef]) =
      if (idleWorkers.isEmpty) (None, Set.empty) else (Some(idleWorkers.head), idleWorkers.tail)
    def taskOption: (Option[TaskRef], Vector[TaskRef]) =
      if (taskQueue.isEmpty) (None, Vector.empty) else (Some(taskQueue.head), taskQueue.tail)
  }

  def apply()(implicit scheduler: Scheduler): Behavior[ServiceCommand] =
    serving(CsvServiceState(1, Vector.empty, Set.empty))

  private def serving(state: CsvServiceState)(implicit scheduler: Scheduler)
  : Behavior[ServiceCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case EnqueueTask(csv, replyTo) =>
        val taskId = state.nextTaskId
        val taskRef = context.spawn(CsvTask(taskId, CsvDetail(csv)), taskName(taskId))
        replyTo ! EnqueueTaskResponse(taskId)
        state.workerOption match {
          case (None, _) =>
            context.log.info(s"Task $taskId enqueued.")
            serving(state.copy(taskQueue = state.taskQueue :+ taskRef, nextTaskId = taskId + 1))
          case (Some(workerRef), otherWorkers) =>
            workerRef ! CsvWorker.ProcessTask(taskRef)
            context.log.info(s"Task $taskId assigned instantly.")
            serving(state.copy(idleWorkers = otherWorkers, nextTaskId = taskId + 1))
        }
      case IdleWorker(workerRef: ActorRef[WorkerCommand]) =>
        state.taskOption match {
          case (None, _) =>
            context.log.info(s"${workerRef.path.name} idles.")
            serving(state.copy(idleWorkers = state.idleWorkers + workerRef))
          case (Some(taskRef), remainingQueue) =>
            workerRef ! CsvWorker.ProcessTask(taskRef)
            context.log.info(s"${taskRef.path.name} assigned to ${workerRef.path.name}.")
            serving(state.copy(taskQueue = remainingQueue))
        }
      case CancelTask(taskId) =>
        context.child(taskName(taskId)) match {
          case Some(taskRef: TaskRef) =>
            taskRef ! CsvTask.Cancel
            val remainingQueue = state.taskQueue.filterNot(_ == taskRef)
            serving(state.copy(taskQueue = remainingQueue))
          case None => Behaviors.same
        }
      case TaskStatus(taskId, replyTo) =>
        context.child(taskName(taskId)) match {
          case Some(taskRef: TaskRef) => taskRef ! CsvTask.StatusReport(replyTo)
          case None => replyTo ! Left(StatusFailure(s"Invalid task ID: $taskId"))
        }
        Behaviors.same
      case ListTasks(replyTo) =>
        implicit val timeout: Timeout = 100.millis
        val result = context.children
          .map(_.unsafeUpcast[TaskCommand])
          .map(_.ask(ref => CsvTask.StatusReport(ref)))
          .map(Await.result(_, timeout.duration))
          .collect {
            case Right(report) => report
          }.toSeq
        replyTo ! result
        Behaviors.same
      case x =>
        context.log.warn(s"Invalid command: $x")
        Behaviors.same
    }
  }

  private def taskName(taskId: TaskId): String = s"task-$taskId"
}
