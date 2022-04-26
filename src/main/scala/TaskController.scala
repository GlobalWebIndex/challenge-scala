package com.gwi.karelsk

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, ChildFailed, Terminated}

import java.net.URI
import java.nio.file.Path
import scala.collection.immutable.Queue

object TaskController {
  import Task._

  sealed trait Command
  final case class CreateTask(uri: URI, replyTo: ActorRef[Id]) extends Command
  final case class ListTasks(replyTo: ActorRef[Seq[Id]]) extends Command
  final case class TaskDetail(id: Id, replyTo: ActorRef[Option[Task]]) extends Command
  final case class CancelTask(id: Id, replyTo: ActorRef[CancelResponse]) extends Command
  final case class WrappedReport(report: TaskWorker.Report) extends Command

  sealed trait CancelResponse
  final case object CancelOk extends CancelResponse
  final case object CancelNotFound extends CancelResponse
  final case object CancelNotCancellable extends CancelResponse

  def apply(tempDir: Path, runningLimit: Int = 2): Behavior[Command] = Behaviors setup { context =>
    val workersReportTo: ActorRef[TaskWorker.Report] = context.messageAdapter(WrappedReport)

    case class State(tasks: Map[Id, Task], nextScheduledId: Id, running: Int, queue: Queue[Scheduled])

    def controller(state: State): Behavior[Command] = {
      import state._

      if (running < runningLimit && queue.nonEmpty) {
        val ready = queue.head
        val result = tempDir.resolve(s"task-${ready.id}.json")
        context.log.debug("Starting task: {} (with result temporary file {})", ready.id, result)

        // TODO: dispatcher
        val worker = context.spawn(TaskWorker(ready.id, ready.uri, result, workersReportTo), s"task-worker-${ready.id}")
        context.watch(worker)

        val started = ready.run(worker, result)
        controller(state.copy(tasks = tasks + (started.id -> started), running = running + 1, queue = queue.tail))

      } else Behaviors.receiveMessage[Command] {

        case CreateTask(uri, replyTo) =>
          context.log.info("Creating task for uri: {}", uri)
          val task = Task(nextScheduledId, uri)
          replyTo ! task.id
          controller(state.copy(tasks = tasks + (task.id -> task), nextScheduledId = nextScheduledId.next, queue = queue :+ task))

        case ListTasks(replyTo) =>
          context.log.debug("Listing all tasks")
          replyTo ! tasks.keys.toSeq
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
            val updatedTasks = tasks + (id -> cancelled)
            task match {
              case working: Running =>
                context.stop(working.worker)
                controller(state.copy(tasks = updatedTasks, running = running - 1))
              case _ => controller(state.copy(tasks = updatedTasks, queue = queue filterNot (_.id == id)))
            }
          }

          updatedBehavior getOrElse {
            replyTo ! (if (tasks.contains(id)) CancelNotCancellable else CancelNotFound)
            Behaviors.same
          }

        case WrappedReport(TaskWorker.Working(id, p)) =>
          context.log.debug("Progress of {}: {}", id, p)
          tasks get id match {
            case Some(task: Running) =>
              controller(state.copy(tasks = tasks + (id -> (task update p))))
            case _ => Behaviors.same
          }

        case WrappedReport(TaskWorker.Finished(id, p)) =>
          context.log.debug("Finished {}: {}", id, p)
          tasks get id match {
            case Some(task: Running) =>
              controller(state.copy(tasks = tasks + (id -> (task update p).done), running = running - 1))
            case _ => Behaviors.same
          }

      } receiveSignal {
        case (_, Terminated(child)) =>
          Behaviors.same
        case (_, ChildFailed(child, cause)) =>
          tasks.values.collectFirst { case t: Running if t.worker == child => t } map { task =>
            context.log.error("Task {} failed ({})", task.id, cause)
            controller(state.copy(tasks = tasks + (task.id -> task.fail(cause)), running = running - 1))
          } getOrElse {
            throw new InternalError(s"Actor $child failed but it was not started by any task")
          }

        case (_, s) =>
          context.log.debug("Signal {} received", s)
          Behaviors.same
      }
    }

    controller(State(Map.empty, initialId, 0, Queue.empty))
  }
}
