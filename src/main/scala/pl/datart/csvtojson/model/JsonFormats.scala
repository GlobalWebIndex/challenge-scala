package pl.datart.csvtojson.model

import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

object JsonFormats extends {
  implicit val rawUriFormat: RootJsonFormat[RawUri] = jsonFormat1(RawUri)
  implicit val taskIdFormat: RootJsonFormat[TaskId] = jsonFormat1(TaskId)
}
