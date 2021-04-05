package gwi

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ ExceptionHandler => EH, _ }

object ExceptionHandler {
  def apply(): EH = EH { case BadRequestError(msg) => discardAndComplete(BadRequest -> msg) }

  private def discardAndComplete(m: => ToResponseMarshallable): Route =
    extractRequest { request =>
      extractMaterializer { implicit mat =>
        request.discardEntityBytes()
        complete(m)
      }
    }
}
