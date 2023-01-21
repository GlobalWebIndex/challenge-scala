package models

import akka.actor.typed.ActorRef
import akka.http.scaladsl.model.Uri

import java.nio.file.Path

sealed trait ConversionMessage
object ConversionMessage {
  final case class CreateTask(
      taskId: TaskId,
      url: Uri,
      result: Path,
      replyTo: ActorRef[TaskInfo]
  ) extends ConversionMessage
  final case class ListTasks(replyTo: ActorRef[Seq[TaskShortInfo]])
      extends ConversionMessage
  final case class GetTask(taskId: TaskId, replyTo: ActorRef[Option[TaskInfo]])
      extends ConversionMessage
  final case class CancelTask(taskId: TaskId, replyTo: ActorRef[Boolean])
      extends ConversionMessage
}
