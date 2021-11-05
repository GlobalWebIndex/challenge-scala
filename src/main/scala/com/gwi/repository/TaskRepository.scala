package com.gwi.repository

import akka.Done
import com.gwi.execution.Task

import java.util.UUID
import scala.concurrent.Future

trait TaskRepository {
  def insertTask(task: Task): Future[UUID]
  def updateTask(task: Task): Future[UUID]
  def getTask(taskId: UUID): Future[Option[Task]]
  def setLinesProcessed(taskId: UUID, linesProcessed: Long): Future[Long]
  def getTaskIds: Future[List[UUID]]
}
