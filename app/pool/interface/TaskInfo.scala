package pool.interface

import pool.interface.TaskCurrentState
final case class TaskInfo[ID, OUT](
    taskId: ID,
    linesProcessed: Long,
    runningSince: Long,
    state: TaskCurrentState[OUT]
)
