package pool.interface

final case class TaskInfo[ID, OUT](
    taskId: ID,
    linesProcessed: Long,
    runningSince: Long,
    state: TaskCurrentState[OUT]
)
