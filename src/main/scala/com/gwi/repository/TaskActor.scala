package com.gwi.repository

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.gwi.execution.Task

import java.util.UUID

object TaskActor {
  sealed trait TaskCommand
  case class Upsert(task: Task, replyTo: ActorRef[UUID]) extends TaskCommand
  case class SetLinesProcessed(taskId: UUID, linesProcessed: Long, replyTo: ActorRef[Long]) extends TaskCommand
  case class Get(taskId: UUID, replyTo: ActorRef[Option[Task]]) extends TaskCommand
  case class GetTaskIds(replyTo: ActorRef[Set[UUID]]) extends TaskCommand

  def apply(): Behavior[TaskCommand] = Behaviors.setup(ctx => {
    def state(tasks: Map[UUID, Task]): Behavior[TaskCommand] = Behaviors.receiveMessage[TaskCommand] {
      case GetTaskIds(replyTo) =>
        replyTo ! tasks.keys.toSet
        Behaviors.same
      case Upsert(task, replyTo) =>
        replyTo ! task.id
        state(tasks + (task.id -> task))
      case SetLinesProcessed(taskId, count, replyTo) =>
        tasks.get(taskId).map(_.copy(linesProcessed = count)) match {
          case Some(task) =>
            replyTo ! count
            state(tasks + (task.id -> task))
          case _ =>
            replyTo ! -1
            Behaviors.same
        }
      case Get(taskId, replyTo) =>
        replyTo ! tasks.get(taskId)
        Behaviors.same
    }

    state(Map.empty)
  })
}
