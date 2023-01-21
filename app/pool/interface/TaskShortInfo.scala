package pool.interface

import pool.interface.TaskState
final case class TaskShortInfo[ID](
    taskId: ID,
    state: TaskState
)
