package com.gwi.service

import com.gwi.model.{CancelTaskException, TaskDetail, TaskState}
import com.gwi.storage.TaskStorage

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class TaskServiceImpl(taskStorage: TaskStorage)(implicit ec: ExecutionContext) extends TaskService {
  // TODO fully implement cancel and create task

  override def createTask(): Future[UUID] = taskStorage.upsertTask(TaskDetail(UUID.randomUUID()))

  override def getTask(taskId: UUID): Future[Option[TaskDetail]] = taskStorage.getTask(taskId)

  override def listTaskIds(): Future[List[UUID]] = taskStorage.getTaskIds

  override def cancelTask(taskId: UUID): Future[Either[CancelTaskException, TaskDetail]] = {
    taskStorage.getTask(taskId).flatMap {
      case Some(task) if task.state == TaskState.Scheduled || task.state == TaskState.Running =>
        val updatedTask = task.copy(state = TaskState.Canceled)
        taskStorage.upsertTask(updatedTask).map(_ => Right(updatedTask))
      case Some(task) =>
        Future.successful(Left(new CancelTaskException(s"Task state is [${task.state}], only Scheduled or Running tasks can be cancelled")))
      case None => Future.successful(Left(new CancelTaskException(s"Task with id [$taskId] does not exists")))
    }
  }
}
