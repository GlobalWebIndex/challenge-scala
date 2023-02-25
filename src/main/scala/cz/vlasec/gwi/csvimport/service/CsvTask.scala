package cz.vlasec.gwi.csvimport.service

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

/**
 * This actor represents a CSV task and its state of processing.
 */
private[service] object CsvTask {
  sealed trait CsvTaskCommand
  case class Run(replyTo: ActorRef[CsvDetail]) extends CsvTaskCommand
  final case class Finish(url: ResultPath, linesAdded: Long) extends CsvTaskCommand
  case object Fail extends CsvTaskCommand
  case object Cancel extends CsvTaskCommand
  final case class ProcessLines(linesAdded: Long) extends CsvTaskCommand
  final case class StatusReport(replyTo: ActorRef[CsvStatusResponse]) extends CsvTaskCommand

  private sealed trait CsvTaskState {
    def taskId: TaskId
    def linesProcessed: Long
    def millisElapsed: Long
    def statusName: String
    private def avgLinesProcessed: Float = if (millisElapsed > 0) (1000.0F * linesProcessed) / millisElapsed else 0.0F
    def report: Either[StatusFailure, CsvTaskStatusReport] =
      Right(CsvTaskStatusReport(taskId, statusName, linesProcessed, avgLinesProcessed))
  }

  private case class Scheduled(taskId: TaskId, detail: CsvDetail) extends CsvTaskState {
    def linesProcessed = 0
    def millisElapsed = 0
    def statusName = "SCHEDULED"
  }
  private case class Running(taskId: TaskId, linesProcessed: Long, millisStarted: Long) extends CsvTaskState {
    def millisElapsed: Long = System.currentTimeMillis() - millisStarted
    def statusName = "RUNNING"
  }
  private case class Done(taskId: TaskId, linesProcessed: Long, millisElapsed: Long, result: ResultPath) extends CsvTaskState {
    def statusName = "DONE"
    override def report: Either[StatusFailure, CsvTaskStatusReport] = super.report.map(_.copy(result = Some(result)))
  }
  private case class Failed(taskId: TaskId, linesProcessed: Long, millisElapsed: Long) extends CsvTaskState {
    def statusName = "FAILED"
  }
  private case class Canceled(taskId: TaskId, linesProcessed: Long, millisElapsed: Long) extends CsvTaskState {
    def statusName = "CANCELED"
  }

  def apply(taskId: TaskId, detail: CsvDetail): Behavior[CsvTaskCommand] = scheduled(Scheduled(taskId, detail))

  private def scheduled(state: Scheduled): Behavior[CsvTaskCommand] = Behaviors.setup { context =>
    context.log.info(s"Task ${state.taskId} is scheduled.")
    Behaviors.receiveMessage {
      case Run(replyTo) => replyTo ! state.detail; run(Running(state.taskId, 0, System.currentTimeMillis()))
      case Cancel => finished(Canceled(state.taskId, 0, 0))
      case StatusReport(replyTo) => replyTo ! state.report; Behaviors.same
      case x => context.log.warn(s"Invalid command $x"); Behaviors.same
    }
  }

  private def run(state: Running): Behavior[CsvTaskCommand] = Behaviors.setup { context =>
    context.log.info(s"Task ${state.taskId} is running with ${state.linesProcessed} lines processed.")
    Behaviors.receiveMessage {
      case ProcessLines(linesAdded) => run(state.copy(linesProcessed = state.linesProcessed + linesAdded))
      case Finish(url, linesAdded) => finished(Done(state.taskId, state.linesProcessed + linesAdded, state.millisElapsed, url))
      case Fail => finished(Failed(state.taskId, state.linesProcessed, state.millisElapsed))
      case Cancel => finished(Canceled(state.taskId, state.linesProcessed, state.millisElapsed))
      case StatusReport(replyTo) => replyTo ! state.report; Behaviors.same
      case x => context.log.warn(s"Invalid command $x"); Behaviors.same
    }
  }

  private def finished(state: CsvTaskState): Behavior[CsvTaskCommand] = Behaviors.setup { context =>
    context.log.info(s"Task ${state.taskId} is now finished (${state.statusName}).")
    Behaviors.receiveMessage {
      case StatusReport(replyTo) => replyTo ! state.report; Behaviors.same
      case x => context.log.warn(s"Invalid command $x"); Behaviors.same
    }
  }
}
