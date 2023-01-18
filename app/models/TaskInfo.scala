package models

import java.io.File

final case class TaskInfo(
    taskId: String,
    linesProcessed: Int,
    runningSince: Long,
    state: TaskCurrentState
)
