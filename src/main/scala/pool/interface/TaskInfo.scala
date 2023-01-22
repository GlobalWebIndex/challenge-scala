package pool.interface

/** Information about a task
  *
  * @param taskId
  *   Task identifier
  * @param linesProcessed
  *   Number of items already processed
  * @param runningSince
  *   Timestamp (in milliseconds) of the moment the task started working (0 for
  *   tasks that didn't start yet)
  * @param state
  *   Current state of the task
  */
final case class TaskInfo[ID, OUT](
    taskId: ID,
    linesProcessed: Long,
    runningSince: Long,
    state: TaskCurrentState[OUT]
)
