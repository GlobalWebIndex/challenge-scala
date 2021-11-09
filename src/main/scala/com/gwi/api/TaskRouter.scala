package com.gwi.api

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpEntity, Uri}
import akka.http.scaladsl.server.{Directives, Route}
import com.gwi.service.TaskService
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax.EncoderOps

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class TaskRouter(taskService: TaskService)(implicit ec: ExecutionContext, system: ActorSystem)
    extends Directives
    with FailFastCirceSupport
    with JsonCodecs {

  private val logger = Logging.getLogger(system, this.getClass)

  val routes: Route = pathPrefix("task") {
    pathPrefix(JavaUUID) { taskId =>
      getTaskResult(taskId) ~ getTaskDetail(taskId) ~ cancelTask(taskId)
    } ~ pathEndOrSingleSlash {
      getTaskIds ~ createTask
    }
  }

  private def getTaskResult(taskId: UUID) = get {
    pathPrefix("result") {
      taskService.getTaskResult(taskId) match {
        case Some(fileContentSource) => complete(OK, HttpEntity(`application/json`, fileContentSource))
        case None =>
          logger.warning(s"Requested file for [$taskId] does not exist")
          complete(NotFound, s"Requested file for [$taskId] does not exist")
      }
    }
  }

  private def getTaskDetail(taskId: UUID) = get {
    pathEndOrSingleSlash {
      onSuccess(taskService.getTask(taskId)) {
        case Some(taskDetail) if taskDetail.state == TaskState.Running =>
          complete(
            taskService
              .getTaskSource(taskId)
              .map(task => ServerSentEvent(task.asJson.noSpaces))
              .keepAlive(maxIdle = 1.second, () => ServerSentEvent.heartbeat)
          )
        case Some(taskDetail) => complete(OK, taskDetail)
        case None => complete(NotFound, s"Task with id [$taskId] does not exist")
      }
    }
  }

  private def cancelTask(taskId: UUID) = delete {
    onSuccess(taskService.cancelTask(taskId)) {
      case Right(taskId) => complete(Accepted, taskId)
      case Left(cancelTaskErrorMessage) => complete(BadRequest, cancelTaskErrorMessage)
    }
  }

  private def getTaskIds = get {
    onSuccess(taskService.listTaskIds()) { taskIds =>
      complete(OK, TaskListResponse(taskIds))
    }
  }

  private def createTask = post {
    entity(as[TaskCreateRequest]) { taskCreateRequest =>
      Try(Uri(taskCreateRequest.csvUri)) match {
        case Success(uri) =>
          onSuccess(taskService.createTask(uri)) { taskId =>
            complete(Accepted, TaskCreateResponse(taskId))
          }
        case Failure(ex) =>
          logger.error(ex, s"Failed to parse ${taskCreateRequest.csvUri} to URI")
          complete(BadRequest, s"Failed to parse ${taskCreateRequest.csvUri} to URI")
      }

    }
  }

}
