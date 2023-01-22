package models

import io.circe._
import io.circe.generic.semiauto._
import pool.interface.TaskState

final case class TaskDetails(
    linesProcessed: Long,
    avgLinesProcessed: Double,
    state: TaskState,
    result: Option[String]
)
object TaskDetails {
  implicit val taskDetailsEncoder: Encoder[TaskDetails] = deriveEncoder
}
