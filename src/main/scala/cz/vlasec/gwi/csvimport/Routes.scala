package cz.vlasec.gwi.csvimport

import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import cz.vlasec.gwi.csvimport.service.{CsvService, CsvStatusResponse, TaskStatusReport, EnqueueTaskResponse}
import cz.vlasec.gwi.csvimport.service.CsvService.ServiceCommand

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object Routes {
  import CirceSupport._
  import io.circe.syntax._
  import io.circe.generic.auto._

  private def task(taskServiceRef: ActorRef[ServiceCommand])(implicit scheduler: Scheduler): Route = {
    implicit val timeout: Timeout = 200.millis
    pathPrefix("task") {
      concat(
        pathEnd {
          concat(
            get {
              val response: Seq[TaskStatusReport] = Await.result(
                taskServiceRef.ask(ref => CsvService.ListTasks(ref)),
                timeout.duration
              )
              complete(HttpEntity(ContentTypes.`application/json`, response.asJson.toString()))
            },
            post {
              decodeRequest {
                entity(as[EnqueueTaskRequest]) { request =>
                  val response: EnqueueTaskResponse = Await.result(
                    taskServiceRef.ask(ref => CsvService.EnqueueTask(request.csvUrl, ref)),
                    timeout.duration
                  )
                  complete(HttpEntity(ContentTypes.`application/json`, response.asJson.toString()))
                }
              }
            }
          )
        },
        path(RemainingPath) { remainder =>
          val taskId = remainder.toString.toInt
          concat(
            get {
              val response: CsvStatusResponse = Await.result(
                taskServiceRef.ask(ref => CsvService.TaskStatus(taskId, ref)),
                timeout.duration
              )
              response match {
                case Left(_) => complete(404)
                case Right(status) => complete(HttpEntity(ContentTypes.`application/json`, status.asJson.toString()))
              }
            },
            delete {
              taskServiceRef ! CsvService.CancelTask(taskId)
              complete(202)
            }
          )
        }
      )
    }
  }

  private val json: Route =
    pathPrefix("json") {
      concat(
        pathEnd {
          get {
            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "List of all JSON files"))
          }
        },
        path(RemainingPath) { filename =>
          get {
            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Contents of file $filename"))
          }
        }
      )
    }

  def routes(taskServiceRef: ActorRef[ServiceCommand])(implicit scheduler: Scheduler): Route =
    Route.seal(concat(task(taskServiceRef), json))

  case class EnqueueTaskRequest(csvUrl: String)
}
