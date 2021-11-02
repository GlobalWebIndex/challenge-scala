package com.gwi.model

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import java.util.UUID

object TaskDetail {
  implicit val taskDetailResponseEncoder: Encoder[TaskDetail] = deriveEncoder
}

case class TaskDetail(
    id: UUID,
    linesProcessed: Long = 0,
    linesPerSecond: Long = 0,
    state: TaskState = TaskState.Scheduled,
    result: Option[String] = None
)
