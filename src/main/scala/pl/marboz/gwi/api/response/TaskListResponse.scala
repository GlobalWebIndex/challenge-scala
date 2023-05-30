package pl.marboz.gwi.api.response

import io.circe.{Encoder}
import io.circe.generic.semiauto.{deriveEncoder}
import org.http4s.EntityEncoder
import org.http4s.circe.jsonEncoderOf


/** @author
  *   <a href="mailto:marboz85@gmail.com">Marcin Bozek</a> Date: 20.05.2023 15:13
  */
final case class TaskListResponse(
  taskList: List[TaskDetailsResponse]
)

object TaskListResponse {

  implicit val encoder: Encoder[TaskListResponse] = deriveEncoder[TaskListResponse]

  implicit def entityEncoder[F[_]]: EntityEncoder[F, TaskListResponse] =
    jsonEncoderOf

}
