package com.gwi.repository

import com.gwi.model.TaskDetail

import java.util.UUID
import scala.concurrent.Future

trait TaskRepository {
  def upsertTask(task: TaskDetail): Future[UUID]
  def getTask(taskId: UUID): Future[Option[TaskDetail]]
  def getTaskIds: Future[List[UUID]]
}
