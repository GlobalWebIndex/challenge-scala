package models

final case class TaskDetails(
    linesProcessed: Long,
    avgLinesProcessed: Double,
    state: TaskState,
    result: Option[String]
)
