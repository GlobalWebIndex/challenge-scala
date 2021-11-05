package com.gwi.repository

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.gwi.execution.Task
import com.gwi.repository.TaskActor._

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class TaskActorRepository(taskActor: ActorRef)(implicit ec: ExecutionContext) extends TaskRepository {
  implicit val timeout: Timeout = 2.seconds

  override def insertTask(task: Task): Future[UUID] = (taskActor ? Create(task)).mapTo[UUID]

  override def updateTask(task: Task): Future[UUID] = (taskActor ? Update(task)).mapTo[UUID]

  override def getTask(taskId: UUID): Future[Option[Task]] = (taskActor ? Get(taskId)).mapTo[Option[Task]]

  override def setLinesProcessed(taskId: UUID, linesProcessed: Long): Future[Long] =
    (taskActor ? SetLinesProcessed(taskId, linesProcessed)).mapTo[Long]

  override def getTaskIds: Future[List[UUID]] = (taskActor ? GetTaskIds).mapTo[List[UUID]]
}
