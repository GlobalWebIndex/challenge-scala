package gwi

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{get, _}
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.server.directives.ContentTypeResolver.Default
import akka.stream.scaladsl.Source
import gwi.io.{InputStreamProvider, LocalFileOutputStreamProvider, OutputStreamProvider, UrlInputStreamProvider}
import gwi.request_handlers._
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future
import scala.io.StdIn

case class  AppContext(tasks:Tasks[TaskState, Unit], inputStreamProvider: InputStreamProvider, outputStreamProvider: OutputStreamProvider, persistanceBaseUrl: String)

object Server extends App {

  // needed to run the route
  implicit val system = ActorSystem(Behaviors.empty, "SprayExample")
  // needed for the future map/flatmap in the end and future in fetchItem and saveOrder
  implicit val executionContext = system.executionContext

  val port = 8085
  val host = "localhost"

  val inputStreamProvider = new UrlInputStreamProvider()
  val outputStreamProvider = new LocalFileOutputStreamProvider()
  val appContext = AppContext(
    tasks = Tasks[TaskState, Unit](),
    inputStreamProvider = inputStreamProvider,
    outputStreamProvider = outputStreamProvider,
    persistanceBaseUrl = s"http://$host:$port/data/"
  )

  val postTaskRequestHandler = new PostTaskRequestHandler(appContext)
  val getTaskRequestHandler = new GetTaskRequestHandler(appContext)
  val deleteTaskRequestHandler = new DeleteTaskRequestHandler(appContext)

  final case class Task(csvUrl: String)

  // implicits for marshalling / unmarshalling
  implicit val taskFormat = jsonFormat1(Task)
  import TaskState._
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()

  val exceptionsHandler = ExceptionHandler {
    case _: java.lang.IllegalArgumentException => complete(StatusCodes.BadRequest, "Bad request")
    case _: NotFoundException => complete(StatusCodes.NotFound, "Resource was not found")
  }

  val bindingFuture = Http().newServerAt(host, port).bind(route)
  println(s"Server online at http://$host:$port/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done

  def route(): Route = {
    concat(
      get {
        pathPrefix("task" / Remaining) { taskId =>
          handleExceptions(exceptionsHandler) {
            val statusStream: Source[TaskState, NotUsed] = getTaskRequestHandler.handle(taskId)
            complete(statusStream)
          }
        }
      },

      get {
        pathPrefix("json-file" / Remaining) { taskId =>
          getFromFile(s"data/$taskId.json")
        }
      },

      post {
        path("task") {
          entity(as[Task]) { task =>
            val f = Future {
              postTaskRequestHandler.handle(task.csvUrl)
            }
            onSuccess(f) { taskId => complete("Task created: " + taskId)
            }
          }
        }
      },

      delete {
        pathPrefix("task" / Remaining) { taskId =>
          handleExceptions(exceptionsHandler) {
            deleteTaskRequestHandler.handle(taskId)
            complete("Task Deleted")
          }
        }
      },

    )
  }

}