package pool.interface

/** The reason why a task stopped */
trait TaskFinishReason

/** All possible reasons for a task to stop */
object TaskFinishReason {

  /** Task finished normally */
  case object Done extends TaskFinishReason

  /** Task was cancelled */
  case object Cancelled extends TaskFinishReason

  /** Task encountered an error */
  case object Failed extends TaskFinishReason
}
