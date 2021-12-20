package com.krykowski.service

import cats.effect.IO
import com.krykowski.model
import com.krykowski.model.{CsvFileLocation, Task, TaskNotFoundError}

trait TaskService {
  def createTask(csv: CsvFileLocation): IO[Long]

  def getAllTasks(): List[Task]

  def cancelTask(taskId: Long): IO[Either[TaskNotFoundError.type, Unit]]

  def getJson(taskId: Long): IO[Either[model.TaskNotFoundError.type, List[String]]]
}