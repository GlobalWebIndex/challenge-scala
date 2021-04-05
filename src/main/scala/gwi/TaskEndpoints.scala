package gwi

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe.Encoder
import io.circe.syntax._

final class TaskEndpoints(service: TaskService) extends TaskJsonFormats {
  val route: Route = {
    concat(
      pathPrefix("task") {
        concat(
          pathEnd {
            concat(
              post {
                entity(as[NewTaskRequest]) { request =>
                  onSuccess(service.create(request.source)) { id =>
                    complete(NewTaskResponse(id))
                  }
                }
              },
              get {
                onSuccess(service.getAll)(complete(_))
              }
            )
          },
          path(IntNumber.map(TaskId)) { id =>
            concat(
              get {
                onSuccess(service.get(id)) { updates =>
                  extractMaterializer { implicit mat =>
                    handleWebSocketMessages(asWebsocketFlow(updates))
                  }
                }
              },
              delete {
                onSuccess(service.cancel(id))(_ => complete(NoContent))
              }
            )
          }
        )
      },
      path("result" / IntNumber.map(TaskId)) { id =>
        get {
          onSuccess(service.result(id))(data => complete(HttpEntity(`application/json`, data)))
        }
      }
    )
  }

  private def asWebsocketFlow[A: Encoder](source: Source[A, _])(implicit m: Materializer): Flow[Message, Message, _] = {
    val discardInput: Sink[Message, _] = Flow[Message]
      .mapConcat {
        case _: TextMessage => Nil
        case bm: BinaryMessage =>
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }
      .to(Sink.ignore)

    Flow.fromSinkAndSource(discardInput, source.map(a => TextMessage(a.asJson.noSpaces)))
  }
}
