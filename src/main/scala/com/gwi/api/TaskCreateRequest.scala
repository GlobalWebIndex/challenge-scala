package com.gwi.api

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

object TaskCreateRequest {
  implicit val taskCreateRequestDecoder: Decoder[TaskCreateRequest] = deriveDecoder
  implicit val taskCreateRequestEncoder: Encoder[TaskCreateRequest] = deriveEncoder
}

case class TaskCreateRequest(csvUri: String)
