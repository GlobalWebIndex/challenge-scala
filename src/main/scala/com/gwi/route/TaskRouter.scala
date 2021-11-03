package com.gwi.route

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError, NotFound, OK}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity
import com.gwi.model.{TaskCreateRequest, TaskCreateResponse, TaskListResponse}
import com.gwi.service.TaskService
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import java.net.URI
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class TaskRouter(taskService: TaskService)(implicit ec: ExecutionContext, system: ActorSystem)
    extends Directives
    with FailFastCirceSupport {

  private val logger = Logging.getLogger(system, this.getClass)

  val routes: Route = pathPrefix("task") {
    pathPrefix(JavaUUID) { taskId =>
      get {
        pathPrefix("result") {
          taskService.downloadJson(taskId) match {
            case Some(fileContentsSource) => complete(HttpEntity(`text/plain(UTF-8)`, fileContentsSource))
            case None =>
              logger.warning(s"Requested file for [$taskId] does not exist")
              complete(NotFound, s"Requested file for [$taskId] does not exist")
          }
        }
      } ~ get {
        pathEndOrSingleSlash {
          onComplete(taskService.getTask(taskId)) {
            case Success(Some(taskDetail)) => complete(OK, taskDetail)
            case Success(None) => complete(NotFound, s"Task with id [$taskId] does not exist")
            case Failure(ex) =>
              logger.error(ex, s"An error occurred while getting a task")
              complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
          }
        }
      } ~ delete {
        onComplete(taskService.cancelTask(taskId)) {
          case Success(Right(taskDetail)) => complete(OK, taskDetail)
          case Success(Left(cancelTaskErrorMessage)) => complete(BadRequest, cancelTaskErrorMessage)
          case Failure(ex) =>
            logger.error(ex, s"An error occurred while canceling task")
            complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
        }
      }
    } ~ pathEndOrSingleSlash {
      get {
        onComplete(taskService.listTaskIds()) {
          case Success(taskIds) => complete(OK, TaskListResponse(taskIds))
          case Failure(ex) =>
            logger.error(ex, s"An error occurred while listing tasks")
            complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
        }
      } ~ post {
        entity(as[TaskCreateRequest]) { taskCreateRequest =>
          Try(new URI(taskCreateRequest.csvUrl)) match {
            case Success(uri) =>
              onComplete(taskService.createTask(uri)) {
                case Success(taskId) => complete(OK, TaskCreateResponse(taskId))
                case Failure(ex) =>
                  logger.error(ex, s"An error occurred while creating a task")
                  complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
              }
            case Failure(ex) =>
              logger.error(ex, s"Failed to convert ${taskCreateRequest.csvUrl} to URI")
              complete(BadRequest, s"Failed to convert ${taskCreateRequest.csvUrl} to URI")
          }

        }
      }
    }
  }

}
