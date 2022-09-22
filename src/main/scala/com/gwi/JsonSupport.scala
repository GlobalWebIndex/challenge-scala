package com.gwi

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.Uri
import com.gwi.Job._
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, RootJsonFormat}

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
      case _                     => throw DeserializationException("Status unexpected")
    }
  }

  implicit object UriFormat extends RootJsonFormat[Uri] {
    def write(uri: Uri): JsValue = JsString(uri.toString())

    def read(json: JsValue): Uri = json match {
      case JsString(uri) => Uri(uri)
      case _             => throw DeserializationException(s"Uri unexpected x")
    }
  }

//  implicit object ResponseFormat extends RootJsonFormat[TaskRepository.Response] {
//    def write(response: TaskRepository.Response): JsValue = response match {
//      case x: TaskRepository.Failed => taskFailedJsonFormat.write(x)
//      case x: TaskRepository.TaskCreated => taskCreatedJsonFormat.write(x)
//      case x: TaskRepository.TaskDeleted => taskDeletedJsonFormat.write(x)
//    }
//
//    def read(json: JsValue): Status = json match {
//
//      case _                     => throw DeserializationException("Status unexpected")
//    }
//  }

  implicit val taskJsonFormat           = jsonFormat1(TaskRepository.Task)
  implicit val taskCreatedJsonFormat    = jsonFormat1(TaskRepository.TaskCreated)
  implicit val taskDeletedJsonFormat    = jsonFormat1(TaskRepository.TaskDeleted)
  implicit val taskStatusJsonFormat     = jsonFormat5(Job.TaskStatus)
}
