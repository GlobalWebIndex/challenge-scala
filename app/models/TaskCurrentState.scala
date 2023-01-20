package models

import java.nio.file.Path

sealed trait TaskCurrentState
object TaskCurrentState {
  object Scheduled extends TaskCurrentState
  object Running extends TaskCurrentState
  final case class Done(at: Long, result: Path) extends TaskCurrentState
  object Failed extends TaskCurrentState
  object Cancelled extends TaskCurrentState
}
