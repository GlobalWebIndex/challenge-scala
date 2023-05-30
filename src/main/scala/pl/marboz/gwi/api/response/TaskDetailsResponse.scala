package pl.marboz.gwi.api.response

import io.circe.generic.semiauto.deriveEncoder
import io.circe.Encoder
import org.http4s.EntityEncoder
import org.http4s.circe.jsonEncoderOf
import pl.marboz.gwi.application.model.Task

/** @author
  *   <a href="mailto:marboz85@gmail.com">Marcin Bozek</a> Date: 20.05.2023 14:33
  */
final case class TaskDetailsResponse(
  status: String,
  url: String,
  uuid: String
)

object TaskDetailsResponse {

  def apply(task: Task): TaskDetailsResponse = {
    new TaskDetailsResponse(task.status.toString, task.uri.fold("")(u => u.toString()), task.uuid.toString)
  }
  implicit val encoder: Encoder[TaskDetailsResponse] = deriveEncoder[TaskDetailsResponse]

  implicit def entityEncoder[F[_]]: EntityEncoder[F, TaskDetailsResponse] =
    jsonEncoderOf

}