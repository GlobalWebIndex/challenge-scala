package cz.vlasec.gwi.csvimport.service

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

/**
 * This actor represents a CSV task and its state of processing.
 * In Scheduled state, the task awaits being assigned to a worker.
 * In Running state, the task is being processed by a worker and the metrics are updated regularly.
 * In final states (Done, Failed, Canceled) the task only responds to status report.
 */
private[service] object CsvTask {

  sealed trait TaskCommand
  case class Run(workerRef: WorkerRef, replyTo: ActorRef[CsvDetail]) extends TaskCommand
  final case class Finish(url: ResultPath, linesAdded: Long) extends TaskCommand
  case object Fail extends TaskCommand
  case object Cancel extends TaskCommand
  final case class ProcessLines(linesAdded: Long) extends TaskCommand
  final case class StatusReport(replyTo: ActorRef[CsvStatusResponse]) extends TaskCommand

  private sealed trait CsvTaskState {
    def taskId: TaskId
    def linesProcessed: Long
    def millisElapsed: Long
    def statusName: String
    private def avgLinesProcessed: Float = if (millisElapsed > 0) (1000.0F * linesProcessed) / millisElapsed else 0.0F
    def report: Either[StatusFailure, TaskStatusReport] =
      Right(TaskStatusReport(taskId, statusName, linesProcessed, avgLinesProcessed))
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
    override def report: Either[StatusFailure, TaskStatusReport] = super.report.map(_.copy(result = Some(result)))
  }
  private case class Failed(taskId: TaskId, linesProcessed: Long, millisElapsed: Long) extends CsvTaskState {
    def statusName = "FAILED"
  }
  private case class Canceled(taskId: TaskId, linesProcessed: Long, millisElapsed: Long) extends CsvTaskState {
    def statusName = "CANCELED"
  }

  def apply(taskId: TaskId, detail: CsvDetail): Behavior[TaskCommand] = scheduled(Scheduled(taskId, detail))

  private def constantBehaviors(context: ActorContext[TaskCommand], state: CsvTaskState)
  : PartialFunction[TaskCommand, Behavior[TaskCommand]] = {
    case StatusReport(replyTo) =>
      replyTo ! state.report
      Behaviors.same
    case x =>
      context.log.warn(s"Invalid command $x")
      Behaviors.same
  }

  private def scheduled(state: Scheduled): Behavior[TaskCommand] = Behaviors.setup { context =>
    context.log.info(s"Task ${state.taskId} is scheduled.")
    val behaviors: PartialFunction[TaskCommand, Behavior[TaskCommand]] = {
      case Run(workerRef, replyTo) =>
        replyTo ! state.detail
        run(Running(state.taskId, 0, System.currentTimeMillis()), workerRef)
      case Cancel =>
        finished(Canceled(state.taskId, 0, 0))
    }
    Behaviors.receiveMessagePartial(behaviors.orElse(constantBehaviors(context, state)))
  }

  private def run(state: Running, workerRef: WorkerRef)
  : Behavior[TaskCommand] = Behaviors.setup { context =>
    context.log.info(s"Task ${state.taskId} is running with ${state.linesProcessed} lines processed.")
    val behaviors: PartialFunction[TaskCommand, Behavior[TaskCommand]] = {
      case ProcessLines(linesAdded) =>
        run(state.copy(linesProcessed = state.linesProcessed + linesAdded), workerRef)
      case Finish(url, linesAdded) =>
        finished(Done(state.taskId, state.linesProcessed + linesAdded, state.millisElapsed, url))
      case Fail =>
        finished(Failed(state.taskId, state.linesProcessed, state.millisElapsed))
      case Cancel =>
        workerRef ! CsvWorker.CancelTask
        finished(Canceled(state.taskId, state.linesProcessed, state.millisElapsed))
    }
    Behaviors.receiveMessagePartial(behaviors.orElse(constantBehaviors(context, state)))
  }

  private def finished(state: CsvTaskState): Behavior[TaskCommand] = Behaviors.setup { context =>
    context.log.info(s"Task ${state.taskId} is now finished (${state.statusName}).")
    Behaviors.receiveMessagePartial(constantBehaviors(context, state))
  }
}
