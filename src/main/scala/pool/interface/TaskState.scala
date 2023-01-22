package pool.interface

/** Current state of a task, abbreviated */
sealed trait TaskState

/** All possible states of a task, abbreviated */
object TaskState {

  /** Task hasn't started yet */
  object SCHEDULED extends TaskState

  /** Task is running */
  object RUNNING extends TaskState

  /** Task finished normally */
  object DONE extends TaskState

  /** Task has failed */
  object FAILED extends TaskState

  /** Task was cancelled */
  object CANCELLED extends TaskState
}
