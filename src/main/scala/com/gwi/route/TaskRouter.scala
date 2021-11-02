package com.gwi.route

import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError, NotFound, OK}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity
import com.gwi.model.{TaskCreateRequest, TaskCreateResponse, TaskListResponse}
import com.gwi.service.TaskService
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class TaskRouter(taskService: TaskService)(implicit ec: ExecutionContext) extends Directives with FailFastCirceSupport {

  val routes: Route = pathPrefix("task") {
    path(JavaUUID) { taskId =>
      get {
        onComplete(taskService.getTask(taskId)) {
          case Success(Some(taskDetail)) => complete(OK, taskDetail)
          case Success(None) => complete(NotFound, s"Task with id [$taskId] does not exist")
          case Failure(ex) => complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
        }
      } ~ get {
        path("result") {
          taskService.downloadJson(taskId) match {
            case Some(fileContentsSource) => complete(HttpEntity(`text/plain(UTF-8)`, fileContentsSource))
            case None => complete(NotFound, s"Requested file for [$taskId] does not exist")
          }
        }
      } ~ delete {
        onComplete(taskService.cancelTask(taskId)) {
          case Success(Right(taskDetail)) => complete(OK, taskDetail)
          case Success(Left(cancelTaskErrorMessage)) => complete(BadRequest, cancelTaskErrorMessage)
          case Failure(ex) => complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
        }
      }
    } ~ pathEndOrSingleSlash {
      get {
        onComplete(taskService.listTaskIds()) {
          case Success(taskIds) => complete(OK, TaskListResponse(taskIds))
          case Failure(ex) => complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
        }
      } ~ post {
        entity(as[TaskCreateRequest]) { taskCreateRequest =>
          onComplete(taskService.createTask(taskCreateRequest.csvUrl)) {
            case Success(taskId) => complete(OK, TaskCreateResponse(taskId))
            case Failure(ex) => complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
          }
        }
      }
    }
  }

}
