package com.gwi.server.response

import com.gwi.server._
import spray.json.DefaultJsonProtocol._

import java.util.UUID

final case class CreateTaskResponse(id: UUID)

object CreateTaskResponse {
  implicit val format = jsonFormat1(CreateTaskResponse.apply)
}
