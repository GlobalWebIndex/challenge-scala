package com.gwi.service

import akka.NotUsed
import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.gwi.execution.{Task, TaskExecutor}
import com.gwi.api.{TaskDetail, TaskState, TaskTransformations}
import com.gwi.repository.TaskRepository
import com.gwi.storage.TaskStorage

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class TaskServiceImpl(taskRepository: TaskRepository, taskStorage: TaskStorage, taskExecutor: TaskExecutor)(implicit
    ec: ExecutionContext,
    system: ActorSystem
) extends TaskService {

  private val taskStateRefreshInterval = 2.seconds

  override def createTask(csvUri: Uri): Future[UUID] = {
    val newTask = Task(UUID.randomUUID(), csvUri)
    for {
      _ <- taskRepository.upsertTask(newTask)
      _ <- taskExecutor.enqueueTask(newTask.id)
    } yield newTask.id
  }

  override def getTask(taskId: UUID): Future[Option[TaskDetail]] =
    taskRepository.getTask(taskId).map(_.map(TaskTransformations.toTaskDetail))

  override def getTaskSource(taskId: UUID): Source[Option[TaskDetail], Cancellable] =
    Source
      .tick(taskStateRefreshInterval, taskStateRefreshInterval, NotUsed)
      .flatMapConcat(_ => Source.future(getTask(taskId)))
      .takeWhile(_.exists(t => !TaskState.TerminalStates.contains(t.state)))

  override def listTaskIds(): Future[Set[UUID]] =
    taskRepository.getTaskIds

  override def cancelTask(taskId: UUID): Future[Either[String, UUID]] = {
    taskRepository.getTask(taskId).flatMap {
      case Some(task) if task.state == TaskState.Scheduled || task.state == TaskState.Running =>
        taskExecutor.cancelTaskExecution(taskId)
        taskRepository
          .upsertTask(task.copy(endedAt = Some(Instant.now()), state = TaskState.Canceled))
          .flatMap(_ => taskRepository.getTask(taskId))
          .map {
            case Some(t) if t.state == TaskState.Canceled => Right(taskId)
            case Some(t) => Left(s"Task state could not be updated because it's in state ${t.state}")
            case _ => Left(s"Task with id [$taskId] does not exists")
          }
      case Some(task) =>
        Future.successful(Left(s"Task state is [${task.state}], only Scheduled or Running tasks can be cancelled"))
      case None => Future.successful(Left(s"Task with id [$taskId] does not exists"))
    }
  }

  def getTaskResult(taskId: UUID): Option[Source[ByteString, _]] = taskStorage.jsonSource(taskId)
}
