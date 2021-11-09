package com.gwi

import java.util.UUID

package object api {
  case class TaskCreateRequest(csvUri: String)
  case class TaskCreateResponse(taskId: UUID)
  case class TaskListResponse(tasks: Set[UUID])

  case class TaskDetail(
      id: UUID,
      linesProcessed: Long = 0,
      linesPerSecond: Long = 0,
      state: TaskState = TaskState.Scheduled,
      result: Option[String] = None
  )

  sealed trait TaskState
  object TaskState {

    case object Scheduled extends TaskState
    case object Running extends TaskState
    case object Done extends TaskState
    case object Failed extends TaskState
    case object Canceled extends TaskState

    val TerminalStates: Set[TaskState] = Set(Done, Failed, Canceled)
  }
}
