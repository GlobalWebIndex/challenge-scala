package com.gwi.server.response

import spray.json.DefaultJsonProtocol._

case class ReadinessResponse(isReady: Boolean)

object ReadinessResponse {
  implicit val format = jsonFormat1(ReadinessResponse.apply)
}
