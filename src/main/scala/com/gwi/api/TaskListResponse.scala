package com.gwi.api

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import java.util.UUID

object TaskListResponse {
  implicit val taskListResponseEncoder: Encoder[TaskListResponse] = deriveEncoder
}

case class TaskListResponse(tasks: List[UUID])
