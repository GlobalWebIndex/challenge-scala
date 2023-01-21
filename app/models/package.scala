import pool.interface.TaskState

import play.api.libs.json.JsString
import play.api.libs.json.Writes

package object models {
  implicit val taskStateWrites: Writes[TaskState] = Writes[TaskState](_ match {
    case TaskState.SCHEDULED => JsString("SCHEDULED")
    case TaskState.RUNNING   => JsString("RUNNING")
    case TaskState.DONE      => JsString("DONE")
    case TaskState.FAILED    => JsString("FAILED")
    case TaskState.CANCELLED => JsString("CANCELLED")
  })
}
