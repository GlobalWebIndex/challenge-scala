package models

import java.io.File

sealed trait TaskCurrentState
object TaskCurrentState {
  object Scheduled extends TaskCurrentState
  object Running extends TaskCurrentState
  final case class Done(at: Long, result: File) extends TaskCurrentState
  object Failed extends TaskCurrentState
  object Cancelled extends TaskCurrentState
}
