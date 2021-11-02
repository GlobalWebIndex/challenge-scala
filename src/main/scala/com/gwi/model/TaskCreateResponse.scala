package com.gwi.model

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import java.util.UUID

object TaskCreateResponse {
  implicit val taskCreateResponseEncoder: Encoder[TaskCreateResponse] = deriveEncoder
}

case class TaskCreateResponse(taskId: UUID)
