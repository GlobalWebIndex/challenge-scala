package com.gwi.server.response

import spray.json.DefaultJsonProtocol._

case class TaskCancelErrorResponse(message: String)

object TaskCancelErrorResponse {
  implicit val format = jsonFormat1(TaskCancelErrorResponse.apply)
}
