package com.gwi.server.request

import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

final case class CreateTaskRequest(url: String)

object CreateTaskRequest {
  implicit val format: RootJsonFormat[CreateTaskRequest] = jsonFormat1(CreateTaskRequest.apply)
}
