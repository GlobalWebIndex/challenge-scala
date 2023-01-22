package pool.interface

/** Abbreviated information about a task
  *
  * @param taskId
  *   Task identifier
  * @param state
  *   Current state of the task
  */
final case class TaskShortInfo[ID](
    taskId: ID,
    state: TaskState
)
