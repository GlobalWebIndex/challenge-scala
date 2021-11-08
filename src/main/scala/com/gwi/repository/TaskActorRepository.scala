package com.gwi.repository

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.gwi.execution.Task
import com.gwi.repository.TaskActor._

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class TaskActorRepository(taskActor: ActorRef[TaskActor.TaskCommand])(implicit ec: ExecutionContext, system: ActorSystem[_])
    extends TaskRepository {
  implicit val timeout: Timeout = 2.seconds

  override def upsertTask(task: Task): Future[UUID] = taskActor.ask(ref => Upsert(task, ref))

  override def getTask(taskId: UUID): Future[Option[Task]] = taskActor.ask(ref => Get(taskId, ref))

  override def setLinesProcessed(taskId: UUID, linesProcessed: Long): Future[Long] =
    taskActor.ask(ref => SetLinesProcessed(taskId, linesProcessed, ref))

  override def getTaskIds: Future[Set[UUID]] = taskActor.ask(ref => GetTaskIds(ref))
}
