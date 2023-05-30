package pl.marboz.gwi.api.response

import io.circe.{Encoder}
import io.circe.generic.semiauto.{deriveEncoder}
import org.http4s.EntityEncoder
import org.http4s.circe.jsonEncoderOf

/** @author
  *   <a href="mailto:marboz85@gmail.com">Marcin Bozek</a> Date: 20.05.2023 22:24
  */
final case class TaskResponse(
  uuid: String
)
object TaskResponse {

  implicit val taskResponseEncoder: Encoder[TaskResponse] = deriveEncoder[TaskResponse]

  implicit def taskResponseEntityEncoder[F[_]]: EntityEncoder[F, TaskResponse] =
    jsonEncoderOf
}
