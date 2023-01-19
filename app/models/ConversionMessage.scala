package models

import akka.actor.typed.ActorRef

sealed trait ConversionMessage
object ConversionMessage {
  final case class CreateTask(url: String, replyTo: ActorRef[TaskInfo])
      extends ConversionMessage
  final case class ListTasks(replyTo: ActorRef[Seq[TaskInfo]])
      extends ConversionMessage
  final case class GetTask(taskId: String, replyTo: ActorRef[Option[TaskInfo]])
      extends ConversionMessage
  final case class CancelTask(taskId: String, replyTo: ActorRef[Boolean])
      extends ConversionMessage
}
