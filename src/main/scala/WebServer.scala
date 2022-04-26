package com.gwi.karelsk

import akka.NotUsed
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, Uri}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{FileIO, Flow, Source}
import akka.util.{ByteString, Timeout}
import org.slf4j.LoggerFactory
import spray.json._

import java.net.URI
import java.nio.file.Files
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object WebServer extends SprayJsonSupport {
  import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
  import spray.json.DefaultJsonProtocol._

  private val log = LoggerFactory.getLogger("WebServer")
  implicit val timeout: Timeout = 3.seconds
  implicit val jsonSupport: JsonEntityStreamingSupport =
    EntityStreamingSupport.json()
      .withFramingRendererFlow(Flow[ByteString].intersperse(ByteString("\n")).asJava)

  private def route(controller: ActorRef[TaskController.Command], baseUri: Uri)
                   (implicit system: ActorSystem[_]): Route = {
    implicit val executionContext: ExecutionContext = system.executionContext

    pathPrefix("task") {
      concat(
        pathEndOrSingleSlash {
          concat(
            get {
              onSuccess(controller.ask(TaskController.ListTasks)) { taskIds =>
                complete(OK, taskIds)
              }
            },
            post {
              entity(as[String]) { uri =>
                onSuccess(controller.ask(TaskController.CreateTask(new URI(uri), _))) { id =>
                  complete(Created, id.toJson)
                }
              }
            }
          )
        },
        path(LongNumber) { id =>
          concat(
            get {
              onSuccess(stateReporting(id, controller)) {
                case Some(reporting) =>
                  complete(reporting.map(_.toJson(t => baseUri.withPath(Path(s"/task/$t/result")).toString)))
//                  complete(reporting.map(_.toString.toJson))
                case None => complete(NotFound, s"Task $id not found")
              }
            },
            delete {
              onSuccess(controller.ask(TaskController.CancelTask(id, _))) {
                case TaskController.CancelOk => complete(OK, s"Task $id cancelled")
                case TaskController.CancelNotFound => complete(NotFound, s"Task $id not found")
                case TaskController.CancelNotCancellable => complete(BadRequest, s"Task $id is not cancelable")
              }
            }
          )
        },
        path(LongNumber / "result") { id =>
          get {
            onSuccess(controller.ask(TaskController.TaskDetail(id, _))) {
              case Some(task: Task.Done) => complete(HttpEntity(`application/json`, FileIO.fromPath(task.result)))
              case Some(task) if task.isTerminal => complete(BadRequest, s"Result of task $id is not yet available")
              case Some(_) => complete(BadRequest, s"Task $id was cancelled or has failed")
              case None => complete(NotFound, s"Task $id not found")
            }
          }
        }
      )
    }
  }

  def stateReporting(id: Task.Id, controller: ActorRef[TaskController.Command])
                    (implicit system: ActorSystem[_]): Future[Option[Source[Task, NotUsed]]] = {
    implicit val executionContext: ExecutionContext = system.executionContext
    log.debug("Asking first detail of task {}", id)
    controller.ask(TaskController.TaskDetail(id, _)) map { taskOption =>
      taskOption map { task =>
        val moreTasks = Source.unfoldAsync(task.isTerminal) { isFinished =>
          if (isFinished) Future(None)
          else {
            log.debug("Asking next detail of task {}", id)
            controller.ask(TaskController.TaskDetail(id, _)) map { taskOption =>
              taskOption map { t => (t.isTerminal, t) }
            }
          }
        }

        (Source.single(task) ++ moreTasks).throttle(1, 2.seconds)
      }
    }
  }

  sealed trait Message
  private final case class Started(binding: ServerBinding) extends Message
  private final case class StartFailed(cause: Throwable) extends Message
  case object Stop extends Message

  def apply(host: String, port: Int): Behavior[Message] = Behaviors setup { context =>

    implicit val system: ActorSystem[Nothing] = context.system

    val tempDir = Files.createTempDirectory("csv_to_json")
    val baseUri = Uri(scheme = "http").withHost(host).withPort(port)
    val controller = context.spawn(TaskController(tempDir), "TaskController")
    val serverBinding: Future[Http.ServerBinding] = Http().newServerAt(host, port).bind(route(controller, baseUri))

    context.pipeToSelf(serverBinding) {
      case Success(binding) => Started(binding)
      case Failure(ex) => StartFailed(ex)
    }

    def running(binding: ServerBinding): Behavior[Message] =
      Behaviors receiveMessagePartial[Message] {
        case Stop =>
          context.log.info(
            "Stopping server http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort)
          Behaviors.stopped
      } receiveSignal {
        case (_, PostStop) =>
          binding.unbind()
          Behaviors.same
      }

    def starting(wasStopped: Boolean): Behavior[Message] =
      Behaviors receiveMessage[Message] {
        case StartFailed(cause) =>
          throw new RuntimeException("Server failed to start", cause)
        case Started(binding) =>
          context.log.info(
            "Server online at http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort)
          if (wasStopped) context.self ! Stop
          running(binding)
        case Stop =>
          // we got a stop message but haven't completed starting yet,
          // we cannot stop until starting has completed
          starting(wasStopped = true)
      }

    starting(wasStopped = false)
  }

  def main(args: Array[String]): Unit = {
    val host = "localhost"
    val port = 8284
    val system = ActorSystem(WebServer(host, port), "csv-to-json")
    sys.addShutdownHook { system ! Stop }
  }
}
