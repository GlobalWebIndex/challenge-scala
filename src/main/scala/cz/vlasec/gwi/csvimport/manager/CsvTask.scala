package cz.vlasec.gwi.csvimport.manager

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

/**
 * This actor represents a CSV task and its state of processing.
 */
object CsvTask {
  sealed trait CsvTaskCommand
  case object Run extends CsvTaskCommand
  final case class Finish(url: JsonUrl) extends CsvTaskCommand
  case object Fail extends CsvTaskCommand
  case object Cancel extends CsvTaskCommand
  final case class LinesProcessed(linesAdded: Long) extends CsvTaskCommand
  final case class StatusReport(replyTo: ActorRef[CsvTaskStatusReport]) extends CsvTaskCommand

  private sealed trait CsvTaskState {
    def taskId: TaskId
    def linesProcessed: Long
    def millisElapsed: Long
    def statusName: String
    def report: CsvTaskStatusReport = CsvTaskStatusReport(taskId, statusName, linesProcessed, millisElapsed)
  }

  private case class Scheduled(taskId: TaskId) extends CsvTaskState {
    def linesProcessed = 0
    def millisElapsed = 0
    def statusName = "SCHEDULED"
  }
  private case class Running(taskId: TaskId, linesProcessed: Long, millisStarted: Long) extends CsvTaskState {
    def millisElapsed: Long = System.currentTimeMillis() - millisStarted
    def statusName = "RUNNING"
  }
  private case class Done(taskId: TaskId, linesProcessed: Long, millisElapsed: Long, url: JsonUrl) extends CsvTaskState {
    def statusName = "DONE"
  }
  private case class Failed(taskId: TaskId, linesProcessed: Long, millisElapsed: Long) extends CsvTaskState {
    def statusName = "FAILED"
  }
  private case class Canceled(taskId: TaskId, linesProcessed: Long, millisElapsed: Long) extends CsvTaskState {
    def statusName = "CANCELED"
  }

  final case class CsvTaskStatusReport private[CsvTask]
  (
    taskId: TaskId,
    state: String,
    linesProcessed: Long = 0,
    averageLinesProcessed: BigDecimal = 0,
    result: Option[JsonUrl] = None
  )

  def apply(taskId: TaskId): Behavior[CsvTaskCommand] = scheduled(Scheduled(taskId))

  private def scheduled(state: Scheduled): Behavior[CsvTaskCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case Run => run(Running(state.taskId, 0, System.currentTimeMillis()))
      case Cancel => finished(Canceled(state.taskId, 0, 0))
      case StatusReport(replyTo) => replyTo ! state.report; Behaviors.same
      case x => context.log.warn(s"Invalid command $x"); Behaviors.same
    }
  }

  private def run(state: Running): Behavior[CsvTaskCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case LinesProcessed(linesAdded) => run(state.copy(linesProcessed = state.linesProcessed + linesAdded))
      case Finish(url) => finished(Done(state.taskId, state.linesProcessed, state.millisElapsed, url))
      case Cancel => finished(Canceled(state.taskId, state.linesProcessed, state.millisElapsed))
      case StatusReport(replyTo) => replyTo ! state.report; Behaviors.same
      case x => context.log.warn(s"Invalid command $x"); Behaviors.same
    }
  }

  private def finished(state: CsvTaskState): Behavior[CsvTaskCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case StatusReport(replyTo) => replyTo ! state.report; Behaviors.same
      case x => context.log.warn(s"Invalid command $x"); Behaviors.same
    }
  }
}
