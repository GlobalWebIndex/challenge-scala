package com.krykowski.repository

import cats.effect.IO
import com.krykowski.model.{Task, TaskNotFoundError}

trait TaskRepository {

  def createTask(task: Task): IO[Long]

  def getTasks: List[Task]

  def getTask(id: Long): IO[Either[TaskNotFoundError.type, Task]]

  def cancelTask(id: Long): IO[Either[TaskNotFoundError.type, Unit]]
}
