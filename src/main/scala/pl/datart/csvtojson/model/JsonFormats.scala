package pl.datart.csvtojson.model

import spray.json.DefaultJsonProtocol._
import spray.json._

object JsonFormats extends {
  implicit val rawUriFormat: RootJsonFormat[RawUri]       = jsonFormat1(RawUri)
  implicit val taskIdFormat: RootJsonFormat[TaskId]       = jsonFormat1(TaskId)
  implicit val taskStateFormat: RootJsonFormat[TaskState] = new RootJsonFormat[TaskState] {
    override def read(json: JsValue): TaskState = {
      json match {
        case JsString("SCHEDULED") => TaskState.Scheduled
        case JsString("RUNNING")   => TaskState.Running
        case JsString("FAILED")    => TaskState.Failed
        case JsString("CANCELED")  => TaskState.Canceled
        case JsString("DONE")      => TaskState.Done
        case _                     => deserializationError("Invalid state value")
      }
    }

    override def write(obj: TaskState): JsValue = {
      JsString(obj.asString)
    }
  }
  implicit val taskStatsFormat: RootJsonFormat[TaskStats] = jsonFormat4(TaskStats)
}
