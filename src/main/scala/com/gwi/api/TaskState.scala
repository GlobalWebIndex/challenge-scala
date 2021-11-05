package com.gwi.api

import io.circe.Encoder
import io.circe.syntax.EncoderOps

sealed trait TaskState

object TaskState {

  implicit val taskStateEncoder: Encoder[TaskState] = Encoder.instance {
    case Scheduled => "SCHEDULED".asJson
    case Running => "RUNNING".asJson
    case Done => "DONE".asJson
    case Failed => "FAILED".asJson
    case Canceled => "CANCELED".asJson
  }

  case object Scheduled extends TaskState
  case object Running extends TaskState
  case object Done extends TaskState
  case object Failed extends TaskState
  case object Canceled extends TaskState

  val TerminalStates: Set[TaskState] = Set(Done, Failed, Canceled)
}
