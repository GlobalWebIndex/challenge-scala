package cz.vlasec.gwi.csvimport.service

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import CsvWorker._
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.util.Timeout
import cz.vlasec.gwi.csvimport.service.CsvTask.CsvTaskCommand

import scala.collection.Set
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object CsvService {
  private val WorkerCount = 2

  sealed trait CsvServiceCommand
  final case class EnqueueTask(csv: CsvUrl, replyTo: ActorRef[EnqueueCsvTaskResponse]) extends CsvServiceCommand
  final case class TaskStatus(taskId: TaskId, replyTo: ActorRef[Either[StatusFailure, CsvTaskStatusReport]]) extends CsvServiceCommand
  final case class ListTasks(replyTo: ActorRef[Seq[CsvTaskStatusReport]]) extends CsvServiceCommand
  final case class CancelTask(taskId: TaskId) extends CsvServiceCommand
  private[service] final case class WorkerStopped(workerId: Long) extends CsvServiceCommand
  private[service] final case class WorkerIdle(taskId: ActorRef[CsvWorkerCommand]) extends CsvServiceCommand

  private case class CsvServiceState(
                                      nextTaskId: TaskId,
                                      taskQueue: Vector[TaskRef],
                                      idleWorkers: Set[WorkerRef],
                                      currentTasks: Map[TaskRef, WorkerRef]) {
    def workerOption: (Option[WorkerRef], Set[WorkerRef]) =
      if (idleWorkers.isEmpty) (None, Set.empty) else (Some(idleWorkers.head), idleWorkers.tail)
    def taskOption: (Option[TaskRef], Vector[TaskRef]) =
      if (taskQueue.isEmpty) (None, Vector.empty) else (Some(taskQueue.head), taskQueue.tail)
  }

  def apply()(implicit scheduler: Scheduler): Behavior[CsvServiceCommand] = Behaviors.setup { context =>
    val workers = (1 to WorkerCount).map(n => context.spawn(CsvWorker(), workerName(n)) -> n).toMap
    workers.foreach(e => context.watchWith(e._1, WorkerStopped(e._2)))
    overseeing(CsvServiceState(0, Vector.empty, workers.keySet, Map.empty))
  }

  private def overseeing(state: CsvServiceState)(implicit scheduler: Scheduler)
  : Behavior[CsvServiceCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case EnqueueTask(csv, replyTo) =>
        val taskId = state.nextTaskId
        val taskRef = context.spawn(CsvTask(taskId, CsvDetail(csv)), taskName(taskId))
        replyTo ! EnqueueCsvTaskResponse(taskId)
        state.workerOption match {
          case (None, _) =>
            context.log.info(s"Job ${state.nextTaskId} enqueued.")
            overseeing(state.copy(taskQueue = state.taskQueue :+ taskRef, nextTaskId = state.nextTaskId + 1))
          case (Some(workerRef), otherWorkers) =>
            workerRef ! CsvWorker.ConvertCsv(taskRef, context.self)
            context.log.info(s"Job ${state.nextTaskId} assigned instantly.")
            overseeing(state.copy(idleWorkers = otherWorkers, nextTaskId = state.nextTaskId + 1,
              currentTasks = state.currentTasks + (taskRef -> workerRef)))
        }
      case WorkerIdle(workerRef: ActorRef[CsvWorkerCommand]) =>
        val runningTasks = state.currentTasks.filterNot(_._2 == workerRef)
        state.taskOption match {
          case (None, _) =>
            context.log.info(s"Worker ${workerRef.path} idles.")
            overseeing(state.copy(idleWorkers = state.idleWorkers + workerRef, currentTasks = runningTasks))
          case (Some(taskRef), remainingQueue) =>
            workerRef ! CsvWorker.ConvertCsv(taskRef, context.self)
            context.log.info(s"Task ${taskRef.path} assigned to worker ${workerRef.path}.")
            overseeing(state.copy(taskQueue = remainingQueue, currentTasks = runningTasks + (taskRef -> workerRef)))
        }
      case CancelTask(taskId) =>
        context.child(taskName(taskId)) match {
          case Some(taskRef: TaskRef) =>
            taskRef ! CsvTask.Cancel
            state.currentTasks.get(taskRef).foreach { workerRef =>
              context.log.warn(s"Stopping worker ${workerRef.path} to cancel their work.")
              context.stop(workerRef)
            }
            val remainingQueue = state.taskQueue.filterNot(_ == taskRef)
            val runningTasks = state.currentTasks.filterNot(_._1 == taskRef)
            overseeing(state.copy(taskQueue = remainingQueue, currentTasks = runningTasks))
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
        // At this point, I don't even want to refactor it to have separate contexts
        // and actors for tasks and workers. It would save me the filtering, but
        // it would also create some other problems of their own.
        val result = context.children.filter(_.path.name.matches(".*task-\\d+"))
          .map(_.unsafeUpcast[CsvTaskCommand])
          .map(_.ask(ref => CsvTask.StatusReport(ref)))
          .map(Await.result(_, timeout.duration))
          .collect {
            case Right(report) => report
          }.toSeq
        replyTo ! result
        Behaviors.same
      case WorkerStopped(workerId) =>
        val workerRef = context.spawn(CsvWorker(), workerName(workerId))
        context.watchWith(workerRef, WorkerStopped(workerId))
        context.self ! WorkerIdle(workerRef)
        context.log.warn(s"Restarting stopped worker with ID $workerId.")
        Behaviors.same
    }
  }

  private def workerName(workerId: Long): String = s"worker-$workerId"

  private def taskName(taskId: TaskId): String = s"task-$taskId"
}
