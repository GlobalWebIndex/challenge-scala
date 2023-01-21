package pool.interface

final case class TaskShortInfo[ID](
    taskId: ID,
    state: TaskState
)
