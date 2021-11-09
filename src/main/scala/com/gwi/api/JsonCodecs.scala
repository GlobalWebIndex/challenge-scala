package com.gwi.api

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps

trait JsonCodecs {
  implicit val taskCreateRequestDecoder: Decoder[TaskCreateRequest] = deriveDecoder
  implicit val taskCreateRequestEncoder: Encoder[TaskCreateRequest] = deriveEncoder

  implicit val taskCreateResponseEncoder: Encoder[TaskCreateResponse] = deriveEncoder
  implicit val taskCreateResponseDecoder: Decoder[TaskCreateResponse] = deriveDecoder

  implicit val taskDetailResponseEncoder: Encoder[TaskDetail] = deriveEncoder

  implicit val taskListResponseEncoder: Encoder[TaskListResponse] = deriveEncoder

  implicit val taskStateEncoder: Encoder[TaskState] = Encoder.instance(_.toString.toUpperCase.asJson)
}
