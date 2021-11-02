package com.gwi.model

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

object TaskCreateRequest {
  implicit val taskCreateRequestDecoder: Decoder[TaskCreateRequest] = deriveDecoder
}

case class TaskCreateRequest(csvUrl: String)
