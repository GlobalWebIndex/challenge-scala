package models

import io.circe._
import io.circe.generic.semiauto._
import pool.interface.TaskState

final case class TaskShortDetails(
    state: TaskState,
    result: Option[String]
)
object TaskShortDetails {
  implicit val taskShortDetailsEncoder: Encoder[TaskShortDetails] =
    deriveEncoder
}
