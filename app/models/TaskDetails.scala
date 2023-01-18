package models

import play.api.libs.json.Json
import play.api.libs.json.Writes

final case class TaskDetails(
    linesProcessed: Int,
    avgLinesProcessed: Double,
    state: TaskState,
    result: Option[String]
)
