package pl.marboz.gwi.api.response

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import org.http4s.EntityEncoder
import org.http4s.circe.jsonEncoderOf
import pl.marboz.gwi.application.algebra.TaskAlgebra.TaskStatus

/**
 * @author <a href="mailto:marboz85@gmail.com">Marcin Bozek</a>
 * Date: 27.05.2023 16:19
 */
final case class TaskStatusResponse(linesProcessedAmount: Int, totalLinesAmount: Int, taskStatus: String, uri: String)

object TaskStatusResponse {

  def apply(taskStatus: TaskStatus) = {
    new TaskStatusResponse(taskStatus.linesProcessedAmount, taskStatus.totalLinesAmount, taskStatus.taskStatus, taskStatus.uri)
  }
  implicit val encoder: Encoder[TaskStatusResponse] = deriveEncoder[TaskStatusResponse]

  implicit def entityEncoder[F[_]]: EntityEncoder[F, TaskStatusResponse] =
    jsonEncoderOf
}
