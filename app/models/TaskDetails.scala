package models

import pool.interface.TaskState

import play.api.libs.json.{Json, Writes}

final case class TaskDetails(
    linesProcessed: Long,
    avgLinesProcessed: Double,
    state: TaskState,
    result: Option[String]
)
object TaskDetails {
  implicit val taskDetailsWrites: Writes[TaskDetails] = Json.writes[TaskDetails]
}
