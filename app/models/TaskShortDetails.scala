package models

import pool.interface.TaskState

import play.api.libs.json.{Json, Writes}

final case class TaskShortDetails(
    state: TaskState,
    result: Option[String]
)
object TaskShortDetails {
  implicit val taskShortDetailsWrites: Writes[TaskShortDetails] =
    Json.writes[TaskShortDetails]
}
