package com.krykowski.repository

import cats.effect.IO
import com.krykowski.model.{Canceled, Task, TaskNotFoundError}
import org.slf4j.LoggerFactory

trait TaskRepository {

  def createTask(task: Task): IO[Long]

  def getTasks: List[Task]

  def getTask(id: Long): IO[Either[TaskNotFoundError.type, Task]]

  def cancelTask(id: Long): IO[Either[TaskNotFoundError.type, Unit]]
}

class TaskInMemoryRepository extends TaskRepository {
  private val logger = LoggerFactory.getLogger(getClass)

  var tasks: List[Task] = List()

  def createTask(task: Task): IO[Long] = {
    logger.info(s"Creating new task: ${task.toString}")
    val highestId =
      if (tasks.isEmpty) 0L
      else {
        tasks.maxBy(_.id).id.get
      }
    val newTask = Task(Some(highestId + 1L), task.status, task.jsonFileUri)
    logger.info(s"Adding new task: ${newTask.toString} to the buffer")
    tasks = newTask :: tasks
    IO(newTask.id.get)
  }

  def getTasks: List[Task] = {
    logger.info("Fetching all tasks from DB")
    tasks
  }

  def getTask(id: Long): IO[Either[TaskNotFoundError.type, Task]] = {
    logger.info(s"Fetching task with id: $id from DB")
    IO(tasks.filter(task => task.id.get == id) match {
      case task :: _ => Right(task)
      case Nil => Left(TaskNotFoundError)
    })
  }

  def cancelTask(id: Long): IO[Either[TaskNotFoundError.type, Unit]] = {
    logger.info(s"Cancelling task with id: $id in DB")
    getTask(id).map {
      case Left(_) => Left(TaskNotFoundError)
      case Right(task) =>
        val newTask = task.copy(id = task.id, status = Canceled, jsonFileUri = task.jsonFileUri)
        tasks = newTask :: tasks.filterNot(_.id.contains(id))
        Right(())
    }
  }

}
