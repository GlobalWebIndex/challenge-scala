package com.gwi
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatchers, Route}
import akka.stream.scaladsl._
import akka.util.Timeout
import com.gwi.TaskRepository._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
class TaskRoutes(taskRepository: ActorRef[TaskRepository.Command])(implicit val system: ActorSystem[_])
    extends JsonSupport {
  // If ask takes more time than this to complete the request is failed

  private implicit val timeout                                  = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()
  implicit val context                                          = system.executionContext
  def createTask(task: Task): Future[TaskCreated] =
    taskRepository.askWithStatus(CreateTask(task, _))
  def getTasks(): Future[List[Int]] =
    taskRepository.ask(GetTasks(_))
  def getTask(id: Id): Future[Option[Job.TaskStatus]] =
    taskRepository.ask(GetTaskById(id, _))
  def deleteTask(id: Id): Future[TaskDeleted] =
    taskRepository.ask(DeleteTask(id, _))
  def getJsons(): Future[Map[String, Uri]] =
    taskRepository.ask(GetJsons(_))

  // If ask takes more time than this to complete the request is failed

  val taskRoutes: Route =
    pathPrefix("task") {
      concat(
        pathEnd {
          concat(
            get {
              rejectEmptyResponse {
                onSuccess(getTasks) { response =>
                  complete(response)
                }
              }
            },
            post {
              entity(as[Task]) { task =>
                onComplete(createTask(task)) { response =>
                  response match {
                    case Failure(exception) => complete(StatusCodes.InternalServerError,exception.getMessage)
                    case Success(value) => complete(StatusCodes.Created,value)
                  }

                }

              }
            }
          )
        },
        path(PathMatchers.IntNumber) { id: Int =>
          get {
            val source = Source
              .tick(0.second, 2.second, id)
              .mapAsync(1) { x =>
                getTask(x)
              }
              .takeWhile(
                status =>
                  status match {
                    case None        => false
                    case Some(value) => if (value.status == Job.Failed || value.status == Job.Done) false else true
                  },
                true
              )

            complete(source)
          } ~
            delete {
              onSuccess(deleteTask(id)) { performed =>
                complete((StatusCodes.OK, performed))
              }
            }

        }
      )
    } ~
      pathPrefix("jsons") {
        pathEnd {
          get {
            rejectEmptyResponse {
              onSuccess(getJsons) { response =>
                complete(response)
              }
            }
          }
        }
      }
}
