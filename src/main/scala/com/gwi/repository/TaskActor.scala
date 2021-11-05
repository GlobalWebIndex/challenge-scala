package com.gwi.repository

import akka.actor.{Actor, Props}
import com.gwi.execution.Task

import java.util.UUID

object TaskActor {
  case class Create(task: Task)
  case class Update(task: Task)
  case class SetLinesProcessed(taskId: UUID, linesProcessed: Long)
  case class Get(taskId: UUID)
  case object GetTaskIds

  def props: Props = Props[TaskActor]
}

class TaskActor extends Actor {

  import TaskActor._

  override def receive: Receive = state(Map.empty)

  def state(tasks: Map[UUID, Task]): Receive = {
    case GetTaskIds => sender() ! tasks.keys
    case Create(task) =>
      context.become(state(tasks + (task.id -> task)))
      sender() ! task.id
    case Update(task) =>
      context.become(state(tasks + (task.id -> task)))
      sender() ! task.id
    case SetLinesProcessed(taskId, count) =>
      tasks.get(taskId).map(_.copy(linesProcessed = count)).foreach { task =>
        context.become(state(tasks + (task.id -> task)))
      }
      sender() ! count
    case Get(taskId) =>
      sender() ! tasks.get(taskId)
  }

}
