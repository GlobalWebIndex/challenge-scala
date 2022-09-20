package com.gwi
import akka.http.scaladsl.server.PathMatchers
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem

import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.gwi.TaskRepository
import com.gwi.TaskRepository._
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.Uri
import akka.NotUsed
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.common.JsonEntityStreamingSupport
import akka.stream._
import akka.stream.scaladsl._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.unmarshalling.Unmarshal
import scala.concurrent.duration._
import scala.language.postfixOps._
import TaskRepository._
import akka.stream.typed.scaladsl.ActorSource
class TaskRoutes(taskRepository: ActorRef[TaskRepository.Command])(implicit val system: ActorSystem[_])
    extends JsonSupport {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  // If ask takes more time than this to complete the request is failed

  private implicit val timeout                                  = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()
  def createTask(task: Task): Future[TaskCreated] =
    taskRepository.ask(CreateTask(task, _))
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
                val operationPerformed: Future[TaskCreated] = createTask(task)
                onSuccess(operationPerformed) { case x: TaskCreated =>
                  complete(StatusCodes.Created, x)
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
              .takeWhile(status =>
                status match {
                  case None        => false
                  case Some(value) => if (value.status == Job.Failed || value.status == Job.Done) false else true
                }, true
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
