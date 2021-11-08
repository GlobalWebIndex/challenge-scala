package com.gwi.api

import io.circe.Encoder
import io.circe.Decoder
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.util.UUID

object TaskCreateResponse {
  implicit val taskCreateResponseEncoder: Encoder[TaskCreateResponse] = deriveEncoder
  implicit val taskCreateResponseDecoder: Decoder[TaskCreateResponse] = deriveDecoder
}

case class TaskCreateResponse(taskId: UUID)
