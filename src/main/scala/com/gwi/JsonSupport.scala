package com.gwi

import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.DeserializationException
import spray.json.JsString
import spray.json.JsValue
import spray.json.RootJsonFormat
import com.gwi.Job._
import akka.http.scaladsl.model.Uri
import spray.json.JsArray
import spray.json.JsNumber
import spray.json.JsObject

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object StatusFormat extends RootJsonFormat[Status] {
    def write(status: Status): JsValue = status match {
      case Scheduled => JsString("Scheduled")
      case Running   => JsString("Running")
      case Done      => JsString("Done")
      case Canceled  => JsString("Canceled")
      case Failed    => JsString("Failed")
    }

    def read(json: JsValue): Status = json match {
      case JsString("Scheduled") => Scheduled
      case JsString("Running")   => Running
      case JsString("Done")      => Done
      case JsString("Canceled")  => Canceled
      case JsString("Failed")    => Failed
      case _                     => throw new DeserializationException("Status unexpected")
    }
  }

  implicit object UriFormat extends RootJsonFormat[Uri] {
    def write(uri: Uri): JsValue = JsString(uri.toString())

    def read(json: JsValue): Uri = json match {
      case JsString(uri) => Uri(uri)
      case x             => throw new DeserializationException(s"Uri unexpected x")
    }
  }


  implicit val taskJsonFormat        = jsonFormat1(TaskRepository.Task)
  implicit val taskCreatedJsonFormat = jsonFormat1(TaskRepository.TaskCreated)
  implicit val taskDeletedJsonFormat = jsonFormat1(TaskRepository.TaskDeleted)
  implicit val taskStatusJsonFormat  = jsonFormat5(Job.TaskStatus)
}
