package models

final case class TaskInfo(
    taskId: String,
    linesProcessed: Long,
    runningSince: Long,
    state: TaskCurrentState
)
