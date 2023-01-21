package pool.interface

import pool.interface.{TaskInfo, TaskShortInfo}

import akka.actor.typed.ActorRef

sealed trait PoolMessage[ID, IN, OUT]
object PoolMessage {
  final case class CreateTask[ID, IN, OUT](
      taskId: ID,
      url: IN,
      result: OUT,
      replyTo: ActorRef[TaskInfo[ID, OUT]]
  ) extends PoolMessage[ID, IN, OUT]
  final case class ListTasks[ID, IN, OUT](
      replyTo: ActorRef[Seq[TaskShortInfo[ID]]]
  ) extends PoolMessage[ID, IN, OUT]
  final case class GetTask[ID, IN, OUT](
      taskId: ID,
      replyTo: ActorRef[Option[TaskInfo[ID, OUT]]]
  ) extends PoolMessage[ID, IN, OUT]
  final case class CancelTask[ID, IN, OUT](
      taskId: ID,
      replyTo: ActorRef[Boolean]
  ) extends PoolMessage[ID, IN, OUT]
}
