package com.gwi.service

import com.gwi.model.{CancelTaskException, TaskDetail}

import java.util.UUID
import scala.concurrent.Future

trait TaskService {

  def createTask(): Future[UUID]
  def getTask(taskId: UUID): Future[Option[TaskDetail]]
  def listTaskIds(): Future[List[UUID]]
  def cancelTask(taskId: UUID): Future[Either[CancelTaskException, TaskDetail]]

}
