package models

final case class TaskShortDetails(
    state: TaskState,
    result: Option[String]
)
