package com.gwi.execution

import akka.http.scaladsl.model.Uri
import com.gwi.api.TaskState

import java.time.Instant
import java.util.UUID

object Task {
  def canBeProcessed(task: Task): Boolean = task.state == TaskState.Scheduled
}

case class Task(
    id: UUID,
    csvUri: Uri,
    startedAt: Option[Instant] = None,
    endedAt: Option[Instant] = None,
    linesProcessed: Long = 0,
    state: TaskState = TaskState.Scheduled
)
