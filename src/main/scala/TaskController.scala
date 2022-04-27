package com.gwi.karelsk

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, ChildFailed, Terminated}

import java.net.URI
import java.nio.file.Path
import scala.collection.immutable.Queue

object TaskController {
  import Task._

  type TaskWorkerFactory = (Task.Id, URI, Path, ActorRef[TaskWorker.ProgressReport]) => Behavior[Nothing]

  sealed trait Command
  final case class CreateTask(uri: URI, replyTo: ActorRef[Id]) extends Command
  final case class ListTasks(replyTo: ActorRef[Set[Id]]) extends Command
  final case class TaskDetail(id: Id, replyTo: ActorRef[Option[Task]]) extends Command
  final case class CancelTask(id: Id, replyTo: ActorRef[CancelResponse]) extends Command
  final case class WrappedReport(report: TaskWorker.ProgressReport) extends Command

  sealed trait CancelResponse
  final case object CancelOk extends CancelResponse
  final case object CancelNotFound extends CancelResponse
  final case object CancelNotCancellable extends CancelResponse

  def apply(workerFactory: TaskWorkerFactory, tempDir: Path, runningLimit: Int = 2): Behavior[Command] =
    Behaviors setup { context =>
      val workersReportTo: ActorRef[TaskWorker.ProgressReport] = context.messageAdapter(WrappedReport)

      case class State(tasks: Map[Id, Task], nextScheduledId: Id, running: Int, queue: Queue[Scheduled])

      def runningTaskOfChild(child: ActorRef[_], candidates: Iterable[Task]): Running =
        candidates collectFirst { case t: Running if t.worker == child => t } getOrElse {
          throw new InternalError(s"Actor $child is not associated with any running task")
        }

      def controller(state: State): Behavior[Command] = {
        import state._

        if (running < runningLimit && queue.nonEmpty) {
          val ready = queue.head
          val result = tempDir.resolve(s"task-${ready.id}.json")
          context.log.debug("Starting task: {} (with result temporary file {})", ready.id, result)

          // TODO: dispatcher
          val worker = context.spawn[Nothing](
            workerFactory(ready.id, ready.uri, result, workersReportTo),
            name = s"task-worker-${ready.id}"
          )
          context.watch(worker)

          val started = ready.run(worker, result)
          controller(state.copy(
            tasks = tasks.updated(started.id, started),
            running = running + 1,
            queue = queue.tail
          ))

        } else Behaviors.receiveMessage[Command] {

          case CreateTask(uri, replyTo) =>
            context.log.info("Creating task for uri: {}", uri)
            val task = Task(nextScheduledId, uri)
            replyTo ! task.id
            controller(state.copy(
              tasks = tasks.updated(task.id, task),
              nextScheduledId = nextScheduledId.next,
              queue = queue :+ task
            ))

          case ListTasks(replyTo) =>
            context.log.debug("Listing all tasks")
            replyTo ! tasks.keySet
            Behaviors.same

          case TaskDetail(id, replyTo) =>
            context.log.debug("Detail of task: {}", id)
            replyTo ! tasks.get(id)
            Behaviors.same

          case CancelTask(id, replyTo) =>
            context.log.info("Canceling task: {}", id)
            val updatedBehavior = for {
              task <- tasks.get(id)
              cancelled <- task.cancel
            } yield {
              replyTo ! CancelOk
              val updatedTasks = tasks.updated(id, cancelled)
              task match {
                case working: Running =>
                  context.unwatch(working.worker)
                  context.stop(working.worker)
                  controller(state.copy(tasks = updatedTasks, running = running - 1))
                case _ => controller(state.copy(tasks = updatedTasks, queue = queue filterNot (_.id == id)))
              }
            }

            updatedBehavior getOrElse {
              replyTo ! (if (tasks.contains(id)) CancelNotCancellable else CancelNotFound)
              Behaviors.same
            }

          case WrappedReport(TaskWorker.ProgressReport(id, p)) =>
            context.log.debug("Progress of {}: {}", id, p)
            tasks get id match {
              case Some(task: Running) =>
                controller(state.copy(tasks = tasks.updated(id, task update p)))
              case _ => Behaviors.same
            }

        } receiveSignal {

          case (_, Terminated(child)) =>
            val task = runningTaskOfChild(child, tasks.values)
            context.log.info("Task {} finished", task.id)
            controller(state.copy(tasks = tasks.updated(task.id, task.done), running = running - 1))

          case (_, ChildFailed(child, cause)) =>
            val task = runningTaskOfChild(child, tasks.values)
            context.log.error("Task {} failed ({})", task.id, cause)
            controller(state.copy(tasks = tasks.updated(task.id, task fail cause), running = running - 1))

          case (_, s) =>
            context.log.debug("Signal {} received", s)
            Behaviors.same
        }
      }

      controller(State(Map.empty, initialId, 0, Queue.empty))
    }
}
