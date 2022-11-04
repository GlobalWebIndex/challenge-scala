package com.gwi.server

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, common}
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, RejectionHandler, Route}
import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import com.gwi.server.request.CreateTaskRequest
import com.gwi.server.response.{CreateTaskResponse, ErrorResponse, ReadinessResponse}
import com.gwi.service.config.AppConfig
import com.gwi.service.dto.{GetJsonLinesError, TaskCanceledResult}
import com.gwi.service.TaskService
import spray.json.DefaultJsonProtocol

import javax.inject.Singleton

@Singleton
class HttpServer @Inject() (taskService: TaskService, config: AppConfig)(implicit val system: ActorSystem)
    extends LazyLogging
    with SprayJsonSupport
    with DefaultJsonProtocol {

  implicit val jsonStreamingSupport: common.JsonEntityStreamingSupport = EntityStreamingSupport.json()

  def startServer(): Unit =
    Http().newServerAt(config.server.ip, config.server.port).bind(buildRoute())

  def buildRoute(): Route = handleRejections(rejectionHandler()){
    concat(
      pathPrefix("ready") {
        get {
          complete(StatusCodes.OK, ReadinessResponse(taskService.isReady))
        }
      },
      pathPrefix("task") {
        concat(
          pathEnd {
            concat(
              get {
                complete(taskService.getAllTasks())
              },
              post {
                entity(as[CreateTaskRequest]) { createTaskRequest =>
                  val taskId = taskService.createTask(createTaskRequest.url)
                  complete(StatusCodes.Accepted, CreateTaskResponse(taskId))
                }
              }
            )
          },
          (get & path(JavaUUID)) { id =>
            taskService.getTaskInfo(id) match {
            case Left(taskSource) =>
              complete(taskSource)
            case _ =>
              complete(StatusCodes.NotFound, ErrorResponse(s"Task $id not found"))
          }
          },
          (delete & path(JavaUUID)) { id =>
            taskService.cancelTask(id) match {
              case TaskCanceledResult.SUCCESS =>
                complete(StatusCodes.NoContent)
              case TaskCanceledResult.NOT_FOUND =>
                complete(StatusCodes.NotFound, ErrorResponse(s"Task $id not found"))
              case TaskCanceledResult.NOT_CANCELABLE_STATE =>
                complete(StatusCodes.BadRequest, ErrorResponse(s"Task $id is in not cancelable state"))
            }
          },
          path("result") {
            (get & path(JavaUUID)) { taskId =>
              taskService.getJsonLines(taskId) match {
                case Left(lineSource) =>
                  complete(lineSource)
                case Right(GetJsonLinesError.NOT_DONE_STATE) =>
                  complete(StatusCodes.BadRequest, ErrorResponse(s"Task $taskId is not in done state"))
                case _ =>
                  complete(StatusCodes.NotFound, ErrorResponse(s"Task $taskId not found"))
              }
            }
          }
        )
      }
    )
  }

  def rejectionHandler(): RejectionHandler = {
    RejectionHandler.newBuilder()
      .handleNotFound {
        complete(StatusCodes.NotFound, ErrorResponse("Not found"))
      }
      .handleAll[MethodRejection] { methodRejections =>
        val names = methodRejections.map(_.supported.name)
        complete(StatusCodes.MethodNotAllowed, ErrorResponse(s"Can't do that! Supported: ${names.mkString(" or ")}!"))
      }
      .result()
  }

}
