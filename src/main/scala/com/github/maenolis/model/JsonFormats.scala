package com.github.maenolis.model

import spray.json.{
  DefaultJsonProtocol,
  DeserializationException,
  JsString,
  JsValue,
  RootJsonFormat
}

object JsonFormats {

  import DefaultJsonProtocol._

  def enumFormat[T <: Enumeration](implicit enu: T): RootJsonFormat[T#Value] =
    new RootJsonFormat[T#Value] {
      def write(obj: T#Value): JsValue = JsString(obj.toString)

      def read(json: JsValue): T#Value = {
        json match {
          case JsString(txt) => enu.withName(txt)
          case somethingElse =>
            throw DeserializationException(
              s"Expected a value from enum $enu instead of $somethingElse"
            )
        }
      }
    }

  implicit val enumConverter = enumFormat(TaskStatus)

  implicit val taskJsonFormat: RootJsonFormat[Task] = jsonFormat5(Task.apply)
  implicit val taskDetailsJsonFormat: RootJsonFormat[TaskDetailsDto] =
    jsonFormat4(TaskDetailsDto.apply)
  implicit val taskDtoJsonFormat: RootJsonFormat[TaskDto] = jsonFormat1(
    TaskDto.apply
  )
  implicit val tasksJsonFormat: RootJsonFormat[TaskList] = jsonFormat1(
    TaskList.apply
  )
  implicit val taskResultsJsonFormat: RootJsonFormat[TaskResult] = jsonFormat3(
    TaskResult.apply
  )

}
