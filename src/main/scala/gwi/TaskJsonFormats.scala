package gwi

import akka.http.scaladsl.model.Uri
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import scala.util._

trait TaskJsonFormats extends OtherTaskJsonFormats {
  private[TaskJsonFormats] implicit val config: Configuration = Configuration.default
    .withDiscriminator(discriminator = "state")
    .copy(transformConstructorNames = {
      case "ScheduledTask" => "SCHEDULED"
      case "RunningTask"   => "RUNNING"
      case "DoneTask"      => "DONE"
      case "FailedTask"    => "FAILED"
      case "CanceledTask"  => "CANCELED"
    })

  implicit val taskStatsEncoder: Encoder[TaskStats] = deriveConfiguredEncoder
  implicit val throwableEncoder: Encoder[Throwable] = _.getMessage.asJson

  implicit val scheduledTaskEncoder: Encoder[ScheduledTask] = deriveConfiguredEncoder
  implicit val runningTaskEncoder: Encoder[RunningTask] = deriveConfiguredEncoder
  implicit val doneTaskEncoder: Encoder[DoneTask] = deriveConfiguredEncoder
  implicit val failedTaskEncoder: Encoder[FailedTask] = deriveConfiguredEncoder
  implicit val canceledTaskEncoder: Encoder[CanceledTask] = deriveConfiguredEncoder

  implicit val taskEncoder: Encoder[Task] = deriveConfiguredEncoder
}

trait OtherTaskJsonFormats {
  import io.circe.generic.extras.defaults._

  implicit val uriDecoder: Decoder[Uri] = (c: HCursor) =>
    c.as[String] match {
      case Right(str) => Try(Uri(str)).toEither.left.map(_ => DecodingFailure("Uri", c.history))
      case Left(_)    => Left(DecodingFailure("Uri", c.history))
    }

  implicit val uriEncoder: Encoder[Uri] = _.toString().asJson

  implicit val taskIdEncoder: Encoder[TaskId] = deriveUnwrappedEncoder

  implicit val newTaskRequestDecoder: Decoder[NewTaskRequest] = deriveConfiguredDecoder
  implicit val newTaskResponseEncoder: Encoder[NewTaskResponse] = deriveConfiguredEncoder
}
