package pool.internal

import pool.interface.TaskInfo
import pool.interface.TaskShortInfo

import akka.Done
import akka.actor.typed.ActorRef

sealed trait PoolMessage[ID, IN, OUT]
object PoolMessage {
  final case class CreateTask[ID, IN, OUT](
      taskId: ID,
      url: IN,
      result: OUT,
      replyTo: ActorRef[Option[TaskInfo[ID, OUT]]]
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
      replyTo: ActorRef[Option[Long]]
  ) extends PoolMessage[ID, IN, OUT]
  final case class CancelAll[ID, IN, OUT](replyTo: ActorRef[Done])
      extends PoolMessage[ID, IN, OUT]
}
