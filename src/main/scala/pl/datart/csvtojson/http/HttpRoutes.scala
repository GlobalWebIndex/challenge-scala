package pl.datart.csvtojson.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpEntity._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl._
import better.files.File
import pl.datart.csvtojson.model.JsonFormats._
import pl.datart.csvtojson.model._
import pl.datart.csvtojson.service.TaskService.StatsSource
import pl.datart.csvtojson.service._
import pl.datart.csvtojson.util.FAdapter
import pl.datart.csvtojson.util.FAdapter._
import spray.json.DefaultJsonProtocol._

import java.nio.file.Paths
import java.util.UUID
import scala.concurrent.Future
import scala.util._

@SuppressWarnings(
  Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing", "org.wartremover.warts.ImplicitParameter")
)
class HttpRoutes[F[_]](taskEnqueuer: TaskScheduler[F], taskService: TaskService[F])(implicit
    fAdapter: FAdapter[F, Future]
) {
  val routes: Route =
    pathPrefix("task") {
      pathEndOrSingleSlash {
        get {
          onComplete(taskService.getTasks.adapt) {
            case Failure(ex)               =>
              complete(HttpResponse(InternalServerError, entity = s"Could not list tasks, reason: ${ex.getMessage}"))
            case scala.util.Success(tasks) =>
              complete(tasks)
          }
        } ~
          post {
            entity(as[RawUri]) { rawUri =>
              val parsedUri = Try(Uri(rawUri.uri))
              validate(parsedUri.isSuccess, parsedUri.failed.fold(_.getMessage, _.getMessage)) {
                onComplete(taskEnqueuer.schedule(rawUri).adapt) {
                  case Failure(ex)                =>
                    complete(HttpResponse(BadRequest, entity = s"Could not enqueue file, reason: ${ex.getMessage}"))
                  case scala.util.Success(taskId) =>
                    complete(taskId)
                }
              }
            }
          }
      } ~
        path(JavaUUID) { uuid: UUID =>
          pathEndOrSingleSlash {
            get {
              extractMaterializer { materializer =>
                onComplete(taskService.getStats(TaskId(uuid.toString)).adapt) {
                  case Failure(ex)                           =>
                    complete(HttpResponse(InternalServerError, entity = ex.getMessage))
                  case scala.util.Success(Some(statsSource)) =>
                    handleWebSocketMessages(statsFlow(statsSource)(materializer))
                  case _                                     =>
                    complete(NotFound)
                }
              }
            } ~
              delete {
                onComplete(taskEnqueuer.cancelTask(TaskId(uuid.toString)).adapt) {
                  case Failure(ex)                            =>
                    complete(HttpResponse(BadRequest, entity = ex.getMessage))
                  case scala.util.Success(cancellationResult) =>
                    cancellationResult match {
                      case Some(CancellationResult.Canceled)            =>
                        complete(OK)
                      case Some(CancellationResult.NotCanceled(reason)) =>
                        complete(BadRequest, reason)
                      case _                                            =>
                        complete(NotFound)
                    }
                }
              }
          }
        }
    } ~
      path("file" / JavaUUID) { uuid: UUID =>
        pathEndOrSingleSlash {
          get {
            onSuccess(taskService.getTask(TaskId(uuid.toString)).adapt) { taskOption =>
              taskOption.fold(complete(NotFound)) { task =>
                val file = File(Paths.get(System.getProperty("java.io.tmpdir"))) / s"${uuid.toString}.json"
                if (task.isInProgress) {
                  complete(TooEarly, "Scheduled or running. Please wait for the completion.")
                } else if (task.isCanceledOrFailed) {
                  complete(BadRequest, "Task canceled or failed.")
                } else if (!file.exists || !file.isReadable) {
                  complete(InternalServerError, "Result file does not exist or is not readable.")
                } else {
                  val fileSource = Source
                    .fromIterator(() => file.lineIterator)
                    .map(ChunkStreamPart(_))
                  complete(HttpResponse(entity = Chunked(ContentTypes.`application/json`, fileSource)))
                }
              }
            }
          }
        }
      }

  private def statsFlow(statsSource: StatsSource)(implicit materializer: Materializer): Flow[Message, Message, _] = {
    val sink = Flow[Message]
      .mapConcat {
        case binaryMessage: BinaryMessage =>
          binaryMessage.dataStream.runWith(Sink.ignore)
          Nil
        case _                            => Nil
      }
      .to(Sink.ignore)

    Flow.fromSinkAndSource(sink, statsSource)
  }
}
