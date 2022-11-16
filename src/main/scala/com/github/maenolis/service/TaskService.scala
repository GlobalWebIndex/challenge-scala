package com.github.maenolis.service

import com.github.maenolis.model.{Task, TaskDto, TaskRepository, TaskStatus}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.Future

class TaskService(db: Database) {

  import slick.jdbc.PostgresProfile.api._

  def getTasks(): Future[Seq[Task]] = db.run(TaskRepository.tasksQuery.result)

  def getTask(id: Long): Future[Option[Task]] = db.run(TaskRepository.taskByIdQuery(id).result.headOption)

  def insert(taskDto: TaskDto): Future[Int] = db.run(TaskRepository.insertTask(taskDto))

  def cancel(id: Long): Future[Int] = db.run(TaskRepository.updateStatus(id, TaskStatus.Canceled))

}
