package models

import pool.interface.TaskState

import play.api.libs.json.Json
import play.api.libs.json.Writes

final case class TaskShortDetails(
    state: TaskState,
    result: Option[String]
)
object TaskShortDetails {
  implicit val taskShortDetailsWrites: Writes[TaskShortDetails] =
    Json.writes[TaskShortDetails]
}
