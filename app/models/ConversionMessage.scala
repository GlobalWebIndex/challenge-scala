package models

import akka.actor.typed.ActorRef

import java.nio.file.Path

sealed trait ConversionMessage
object ConversionMessage {
  final case class CreateTask(
      taskId: String,
      url: String,
      result: Path,
      replyTo: ActorRef[TaskInfo]
  ) extends ConversionMessage
  final case class ListTasks(replyTo: ActorRef[Seq[TaskShortInfo]])
      extends ConversionMessage
  final case class GetTask(taskId: String, replyTo: ActorRef[Option[TaskInfo]])
      extends ConversionMessage
  final case class CancelTask(taskId: String, replyTo: ActorRef[Boolean])
      extends ConversionMessage
}
