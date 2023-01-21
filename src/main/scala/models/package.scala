import io.circe._
import pool.interface.TaskState

package object models {
  implicit val taskStateWrites: Encoder[TaskState] = Encoder(_ match {
    case TaskState.SCHEDULED => Json.fromString("SCHEDULED")
    case TaskState.RUNNING   => Json.fromString("RUNNING")
    case TaskState.DONE      => Json.fromString("DONE")
    case TaskState.FAILED    => Json.fromString("FAILED")
    case TaskState.CANCELLED => Json.fromString("CANCELLED")
  })
}
