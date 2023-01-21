package models

final case class TaskInfo(
    taskId: TaskId,
    linesProcessed: Long,
    runningSince: Long,
    state: TaskCurrentState
)
