package pool.interface

sealed trait TaskState
object TaskState {
  object SCHEDULED extends TaskState
  object RUNNING extends TaskState
  object DONE extends TaskState
  object FAILED extends TaskState
  object CANCELLED extends TaskState
}
