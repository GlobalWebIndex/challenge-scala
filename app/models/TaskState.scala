package models

import play.api.libs.json.JsString
import play.api.libs.json.Writes

sealed trait TaskState
object TaskState {
  object SCHEDULED extends TaskState
  object RUNNING extends TaskState
  object DONE extends TaskState
  object FAILED extends TaskState
  object CANCELLED extends TaskState
  implicit val taskStateWrites: Writes[TaskState] = Writes[TaskState](_ match {
    case TaskState.SCHEDULED => JsString("SCHEDULED")
    case TaskState.RUNNING   => JsString("RUNNING")
    case TaskState.DONE      => JsString("DONE")
    case TaskState.FAILED    => JsString("FAILED")
    case TaskState.CANCELLED => JsString("CANCELLED")
  })
}
