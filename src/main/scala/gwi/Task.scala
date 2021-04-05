package gwi

import akka.http.scaladsl.model.Uri
import scala.math.BigDecimal.RoundingMode

/** A job which converts a CSV file available at the specified URI to its JSON representation.
  * Individual cases represent the lifecycle of Task.
  */
sealed trait Task {
  def id: TaskId
  def source: Uri
}

object Task {

  /** Checks if the task is in a terminal state. */
  def isTerminal(task: Task): Boolean =
    task match {
      case _: CanceledTask | _: FailedTask | _: DoneTask => true
      case _: ScheduledTask | _: RunningTask             => false
    }
}

/** A new task enqueued by the manager. */
final case class ScheduledTask(id: TaskId, source: Uri) extends Task {
  def run: RunningTask = RunningTask(id, source, None)
  def cancel: CanceledTask = CanceledTask(id, source)
}

/** A task being executed by the manager. Stats contains the latest statistics collected
  * from the worker handling the task; is empty if no data has been reported yet.
  */
final case class RunningTask(id: TaskId, source: Uri, stats: Option[TaskStats]) extends Task {
  def stats(stats: TaskStats): RunningTask = copy(stats = Some(stats))
  def done(result: Uri, stats: TaskStats): DoneTask = DoneTask(id, source, result, stats)
  def cancel: CanceledTask = CanceledTask(id, source)
  def fail(cause: Throwable): FailedTask = FailedTask(id, source, cause)
}

/** A successfully finished task. Result contains public URI from which consumers can
  * download converted JSON file.
  */
final case class DoneTask(id: TaskId, source: Uri, result: Uri, stats: TaskStats) extends Task

/** A task aborted because of an error. */
final case class FailedTask(id: TaskId, source: Uri, cause: Throwable) extends Task

/** A task aborted by user. */
final case class CanceledTask(id: TaskId, source: Uri) extends Task

final case class TaskId(value: Int) extends AnyVal {
  def +(i: Int): TaskId = TaskId(value + i)
}

object TaskStats {

  /** Calculate final stats. */
  def overall(linesProcessed: BigDecimal, millis: BigDecimal, scale: Int = 2): TaskStats =
    TaskStats(linesProcessed, (linesProcessed / (millis / 1000)).setScale(scale, RoundingMode.HALF_UP))
}

final case class TaskStats(linesProcessed: BigDecimal, linesProcessedPerSec: BigDecimal)

/** A raw request to enqueue a new task received from the REST api. */
final case class NewTaskRequest(source: Uri)

/** Response message sent via REST api after enqueueing a new task. */
final case class NewTaskResponse(id: TaskId)
