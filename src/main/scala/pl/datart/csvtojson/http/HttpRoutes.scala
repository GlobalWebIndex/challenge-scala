package pl.datart.csvtojson.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import pl.datart.csvtojson.model.JsonFormats._
import pl.datart.csvtojson.model._
import pl.datart.csvtojson.service.TaskEnqueuer
import pl.datart.csvtojson.util.FConverter
import pl.datart.csvtojson.util.FConverter._

import scala.concurrent.Future
import scala.util._

@SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
class HttpRoutes[F[_]](taskEnqueuer: TaskEnqueuer[F])(implicit fConverter: FConverter[F, Future]) {
  val routes: Route =
    path("task") {
      post {
        entity(as[RawUri]) { rawUri =>
          onComplete(taskEnqueuer.enqueue(rawUri).toFuture) {
            case Failure(ex)     =>
              complete(HttpResponse(BadRequest, entity = s"Could not enqueue file, reason: ${ex.getMessage}"))
            case Success(taskId) =>
              complete(taskId)
          }
        }
      }
    }
}
