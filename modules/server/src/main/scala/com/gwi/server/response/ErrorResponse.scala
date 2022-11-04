package com.gwi.server.response

import spray.json.DefaultJsonProtocol._

case class ErrorResponse(message: String)

object ErrorResponse {
  implicit val format = jsonFormat1(ErrorResponse.apply)
}
