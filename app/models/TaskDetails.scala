package models

import pool.interface.TaskState

import play.api.libs.json.Json
import play.api.libs.json.Writes

final case class TaskDetails(
    linesProcessed: Long,
    avgLinesProcessed: Double,
    state: TaskState,
    result: Option[String]
)
object TaskDetails {
  implicit val taskDetailsWrites: Writes[TaskDetails] = Json.writes[TaskDetails]
}
