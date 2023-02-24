package cz.vlasec.gwi.csvimport.service

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import CsvWorker._
import akka.util.Timeout
import cz.vlasec.gwi.csvimport.service.CsvTask.{CsvDetail, CsvTaskStatusReport, StatusFailure, StatusReport}

import scala.collection.Set
import scala.concurrent.duration.DurationInt

object CsvService {
  private type Workers = Set[WorkerRef]
  private type Tasks = Vector[TaskRef]

  private val WorkerCount = 2

  sealed trait CsvServiceCommand
  final case class ConvertCsv(csv: CsvUrl, replyTo: ActorRef[TaskId]) extends CsvServiceCommand
  final case class GetStatus(taskId: TaskId, replyTo: ActorRef[Either[StatusFailure, CsvTaskStatusReport]]) extends CsvServiceCommand
  private[service] final case class WorkerIdle(taskId: ActorRef[CsvWorkerCommand]) extends CsvServiceCommand

  private case class CsvServiceState(nextTaskId: TaskId, taskQueue: Tasks, idleWorkers: Workers) {
    def workerOption: (Option[WorkerRef], Set[WorkerRef]) =
      if (idleWorkers.isEmpty) (None, Set.empty) else (Some(idleWorkers.head), idleWorkers.tail)
    def taskOption: (Option[TaskRef], Vector[TaskRef]) =
      if (taskQueue.isEmpty) (None, Vector.empty) else (Some(taskQueue.head), taskQueue.tail)
  }

  def apply()(implicit scheduler: Scheduler): Behavior[CsvServiceCommand] = Behaviors.setup {
    context =>
      val workers: Workers = (1 to WorkerCount).map(n => context.spawn(CsvWorker(), s"worker-$n")).toSet
      overseeing(CsvServiceState(0, Vector.empty, workers))
  }

  private def overseeing(state: CsvServiceState)(implicit scheduler: Scheduler): Behavior[CsvServiceCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case ConvertCsv(csv, replyTo) =>
        val taskId = state.nextTaskId
        val taskRef = context.spawn(CsvTask(taskId, CsvDetail(csv)), taskName(taskId))
        replyTo ! taskId
        state.workerOption match {
          case (None, _) =>
            context.log.info(s"Job ${state.nextTaskId} enqueued.")
            overseeing(state.copy(taskQueue = state.taskQueue :+ taskRef, nextTaskId = state.nextTaskId + 1))
          case (Some(workerRef), otherWorkers) =>
            workerRef ! CsvWorker.ConvertCsv(taskRef, context.self)
            context.log.info(s"Job ${state.nextTaskId} assigned instantly.")
            overseeing(state.copy(idleWorkers = otherWorkers, nextTaskId = state.nextTaskId + 1))
        }
      case GetStatus(taskId, replyTo) =>
        context.child(taskName(taskId)) match {
          case Some(taskRef: TaskRef) => taskRef ! StatusReport(replyTo)
          case None => replyTo ! Left(StatusFailure(s"Invalid task ID: $taskId"))
        }
        Behaviors.same
      case WorkerIdle(workerRef: ActorRef[CsvWorkerCommand]) =>
        state.taskOption match {
          case (None, _) =>
            context.log.info(s"Worker ${workerRef.path} idles.")
            overseeing(state.copy(idleWorkers = state.idleWorkers + workerRef))
          case (Some(taskRef), remainingQueue) =>
            workerRef ! CsvWorker.ConvertCsv(taskRef, context.self)
            context.log.info(s"Task ${taskRef.path} assigned to worker ${workerRef.path}.")
            overseeing(state.copy(taskQueue = remainingQueue))
        }
    }
  }

  private def taskName(taskId: TaskId): String = s"task-$taskId"
}
