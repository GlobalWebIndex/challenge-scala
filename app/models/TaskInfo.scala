package models

import java.io.File

final case class TaskInfo(
    taskId: String,
    linesProcessed: Long,
    runningSince: Long,
    state: TaskCurrentState
)
