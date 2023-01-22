package pool.interface

/** The reason why a task stopped */
trait TaskFinishReason

/** All possible reasons for a task to stop */
object TaskFinishReason {

  /** Task finished normally */
  object Done extends TaskFinishReason

  /** Task was cancelled */
  object Cancelled extends TaskFinishReason

  /** Task encountered an error */
  object Failed extends TaskFinishReason
}
