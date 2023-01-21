package pool.interface

sealed trait TaskCurrentState[OUT]
object TaskCurrentState {
  final case class Scheduled[OUT]() extends TaskCurrentState[OUT]
  final case class Running[OUT]() extends TaskCurrentState[OUT]
  final case class Finished[OUT](
      at: Long,
      result: OUT,
      reason: TaskFinishReason
  ) extends TaskCurrentState[OUT]
}
