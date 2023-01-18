package models

sealed trait TaskCurrentState
object TaskCurrentState {
  object Scheduled extends TaskCurrentState
  object Running extends TaskCurrentState
  final case class Done(at: Long) extends TaskCurrentState
  object Failed extends TaskCurrentState
  object Cancelled extends TaskCurrentState
}
