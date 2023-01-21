package pool.interface

sealed trait TaskCurrentState[OUT]
object TaskCurrentState {
  final case class Scheduled[OUT]() extends TaskCurrentState[OUT]
  final case class Running[OUT]() extends TaskCurrentState[OUT]
  final case class Done[OUT](at: Long, result: OUT)
      extends TaskCurrentState[OUT]
  final case class Failed[OUT]() extends TaskCurrentState[OUT]
  final case class Cancelled[OUT]() extends TaskCurrentState[OUT]
}
