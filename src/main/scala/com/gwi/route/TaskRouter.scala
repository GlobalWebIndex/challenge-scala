package com.gwi.route

import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError, NotFound, OK}
import akka.http.scaladsl.server.{Directives, Route}
import com.gwi.model.{TaskCreateResponse, TaskListResponse}
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
          case Success(None) => complete(NotFound, s"Task with id [$taskId] does not exists")
          case Failure(ex) => complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
        }
      } ~ delete {
        onComplete(taskService.cancelTask(taskId)) {
          case Success(Right(taskDetail)) => complete(OK, taskDetail)
          case Success(Left(cancelTaskException)) => complete(BadRequest, cancelTaskException.message)
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
        onComplete(taskService.createTask()) {
          case Success(taskId) => complete(OK, TaskCreateResponse(taskId))
          case Failure(ex) => complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
        }
      }
    }
  }

}
