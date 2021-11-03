package com.gwi.service

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.gwi.model.{TaskDetail, TaskState}
import com.gwi.repository.TaskRepository
import com.gwi.storage.TaskStorage

import java.net.URI
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class TaskServiceImpl(taskRepository: TaskRepository, taskStorage: TaskStorage)(implicit ec: ExecutionContext, system: ActorSystem)
    extends TaskService {
  private val logger = Logging.getLogger(system, this.getClass)

  // TODO fully implement cancel and create task

  override def createTask(csvUrl: URI): Future[UUID] =
    taskRepository.upsertTask(TaskDetail(UUID.randomUUID()))

  override def getTask(taskId: UUID): Future[Option[TaskDetail]] = taskRepository.getTask(taskId)

  override def listTaskIds(): Future[List[UUID]] = taskRepository.getTaskIds

  override def cancelTask(taskId: UUID): Future[Either[String, TaskDetail]] = {
    taskRepository.getTask(taskId).flatMap {
      case Some(task) if task.state == TaskState.Scheduled || task.state == TaskState.Running =>
        val updatedTask = task.copy(state = TaskState.Canceled)
        taskRepository.upsertTask(updatedTask).map(_ => Right(updatedTask))
      case Some(task) =>
        Future.successful(Left(s"Task state is [${task.state}], only Scheduled or Running tasks can be cancelled"))
      case None => Future.successful(Left(s"Task with id [$taskId] does not exists"))
    }
  }

  def downloadJson(taskId: UUID): Option[Source[ByteString, _]] = taskStorage.taskJsonSource(taskId)
}
