package pool.interface

trait TaskFinishReason
object TaskFinishReason {
  object Done extends TaskFinishReason
  object Cancelled extends TaskFinishReason
  object Failed extends TaskFinishReason
}
