package com.gwi.api

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

object TaskCreateRequest {
  implicit val taskCreateRequestDecoder: Decoder[TaskCreateRequest] = deriveDecoder
}

case class TaskCreateRequest(csvUri: String)
