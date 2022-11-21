package com.github.maenolis.server

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, NoContent}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.{CompactByteString, Timeout}
import com.github.maenolis.model.{JsonFormats, TaskDto, TaskList}
import com.github.maenolis.service.TaskService

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class TaskRoutes(taskService: TaskService)(implicit
    val system: ActorSystem[_]
) {

  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  private implicit val timeout: Timeout = Timeout.create(
    system.settings.config.getDuration("csv-app.routes.ask-timeout")
  )

  implicit val ec: ExecutionContext = system.executionContext

  def getTasksRoute: Route = get {
    onComplete(taskService.getTasks().map(tasks => TaskList(tasks))) {
      case Success(tasks) => complete(tasks)
      case Failure(ex) =>
        complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
    }
  }

  def getTaskRoute(id: Long): Route = get {

    import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._

    complete {
      Source
        .tick(2.seconds, 2.seconds, NotUsed)
        .mapAsync(1)(_ => {
          taskService.getTaskDetails(id)
        })
        .filter(_.nonEmpty)
        .map(task => ServerSentEvent(task.get.toString))
        .keepAlive(2.second, () => ServerSentEvent.heartbeat)
    }
  }

  def getTaskJsonRoute(id: Long): Route = get {
    respondWithHeaders(
      RawHeader("Content-Disposition", s"""attachment; filename="$id.json"""")
    ) {
      complete(
        HttpEntity(
          ContentTypes.`application/octet-stream`,
          Source
            .fromPublisher(taskService.getTaskJson(id))
            .map(taskResult =>
              CompactByteString(
                JsonFormats.taskResultsJsonFormat.write(taskResult).toString()
              )
            )
        )
      )
    }
  }

  def postTaskRoute: Route = post {
    decodeRequest {
      entity(as[TaskDto]) { taskDto =>
        onComplete(taskService.insert(taskDto)) {
          case Success(_) => complete(NoContent)
          case Failure(ex) =>
            complete(
              InternalServerError,
              s"An error occurred: ${ex.getMessage}"
            )
        }
      }
    }
  }

  def cancelTaskRoute(id: Long): Route = delete {
    onComplete(taskService.cancel(id)) {
      case Success(_) => complete(NoContent)
      case Failure(ex) =>
        complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
    }
  }

  def allRoutes(): Route =
    path("tasks")(getTasksRoute) ~
      path("tasks")(postTaskRoute) ~
      pathPrefix("tasks" / LongNumber)(getTaskRoute) ~
      pathPrefix("json" / LongNumber)(getTaskJsonRoute) ~
      pathPrefix("tasks" / LongNumber)(cancelTaskRoute)

}
