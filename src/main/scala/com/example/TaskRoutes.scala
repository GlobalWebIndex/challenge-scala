package com.example

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.example.TaskActor._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.Future

class TaskRoutes(taskActor: ActorRef[TaskMessage])(implicit val system: ActorSystem[_]) extends FailFastCirceSupport {
  import TaskRoutes._
  import io.circe.generic.auto._

  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("server.ask-timeout"))
  final val OUTPUT_FOLDER = system.settings.config.getString("downloader.output-folder")
  final val SERVER_HOST = system.settings.config.getString("server.host")
  final val SERVER_PORT = system.settings.config.getInt("server.port")

  def listTasks(): Future[ListTasksResponse] =
    taskActor.ask(ListTasks)
  def createTask(csvUri: String): Future[Task] =
    taskActor.ask(CreateTask(csvUri, _))
  def cancelTask(id: Int): Future[TaskActorResponse] =
    taskActor.ask(CancelTask(id, _))

  val taskRoutes: Route =
    pathPrefix("tasks") {
      concat(
        pathEnd {
          concat(
            get {
              complete(listTasks())
            },
            post {
              entity(as[CreateTaskRequest]) { createTaskRequest =>
                onSuccess(createTask(createTaskRequest.csvUri)) { performed =>
                  complete((StatusCodes.Created, performed))
                }
              }
            })
        },
        path(Segment) { name =>
            delete {
              onSuccess(cancelTask(name.toInt)) { performed =>
                complete((StatusCodes.OK, performed))
              }
            }
        })
    } ~
      pathPrefix("files") {
        path(Segment) { id =>
          getFromFile(s"$OUTPUT_FOLDER$id.json")
        }
      }
}

object TaskRoutes {
  final case class CreateTaskRequest(csvUri: String)
}
