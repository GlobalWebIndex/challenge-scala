package pl.marboz.gwi.api.request

import cats.effect.{Async, Concurrent}
import cats.implicits.toBifunctorOps
import io.circe.Decoder
import io.circe.generic.semiauto._
import org.http4s.{EntityDecoder, Uri}
import org.http4s.circe.jsonOf

/** @author
  *   <a href="mailto:marboz85@gmail.com">Marcin Bozek</a> Date: 20.05.2023 14:05
  */
final case class TaskRequest(
  uri: Uri
)

object TaskRequest {

  implicit val taskRequestDecoder: Decoder[TaskRequest] = deriveDecoder[TaskRequest]

  implicit def taskRequestEntityDecoder[F[_]: Concurrent: Async]: EntityDecoder[F, TaskRequest] =
    jsonOf

  implicit val uriDecoder: Decoder[Uri] = Decoder.decodeString.emap { str =>
    Uri.fromString(str).leftMap(_.toString)
  }

  implicit def uriEntityDecoder[F[_] : Concurrent : Async]: EntityDecoder[F, Uri] =
    jsonOf

}