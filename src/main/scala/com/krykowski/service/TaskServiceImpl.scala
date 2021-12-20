package com.krykowski.service

import cats.effect.{Blocker, IO}
import com.krykowski.model
import com.krykowski.model.{CsvFileLocation, Done, Running, Scheduled, Task, TaskNotFoundError}
import com.krykowski.repository.TaskRepository
import com.softwaremill.sttp.{Response, TryHttpURLConnectionBackend, UriContext, sttp}
import io.circe.generic.auto.exportEncoder
import io.circe.syntax.EncoderOps
import org.slf4j.LoggerFactory

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import sys.process._
import java.net.URL
import java.nio.file.{Path, Paths}

trait TaskService {
  def createTask(csv: CsvFileLocation): IO[Long]

  def getAllTasks(): List[Task]

  def cancelTask(taskId: Long): IO[Either[TaskNotFoundError.type, Unit]]

  def getJson(taskId: Long): IO[Either[model.TaskNotFoundError.type, List[String]]]
}

class TaskServiceImpl(taskRepository: TaskRepository) extends TaskService {
  private val logger = LoggerFactory.getLogger(getClass)

  implicit private val cs = IO.contextShift(global)
  implicit private val cf = IO.ioConcurrentEffect

  override def createTask(csv: CsvFileLocation): IO[Long] = {
    getCsvFile(csv.uri) match {
      case Left(ex) =>
        logger.info(s"Couldn't download the file: ${ex.toString}")
        Left(ex)
      case Right(_) => fileDownloader(csv.uri, s"${csv.name}.csv")
    }
    val numberOfRunningTasks = taskRepository.getTasks.count(task => task.status == Running)
    logger.info(s"Number of running tasks: $numberOfRunningTasks")
    val jsonFileName = s"${csv.name}.json"
    logger.info(s"Json file will be saved to: $jsonFileName")
    val task = if (numberOfRunningTasks < 2) {
      Task(None, Running, jsonFileName)
      val downloadedCsv = Paths.get(s"${csv.name}.csv")
      transformToJson(downloadedCsv, jsonFileName)
      Task(None, Done, jsonFileName)
    }
    else {
      Task(None, Scheduled, jsonFileName)
    }
    taskRepository.createTask(task)
  }

  override def getAllTasks(): List[Task] = taskRepository.getTasks

  override def cancelTask(taskId: Long): IO[Either[TaskNotFoundError.type, Unit]] = taskRepository.cancelTask(taskId)

  override def getJson(taskId: Long): IO[Either[TaskNotFoundError.type, List[String]]] = {
    logger.info(s"Fetching json from file for task with id: $taskId")
    taskRepository.getTask(taskId).map {
      case Left(error) => Left(error)
      case Right(task) => Right(streamJsonFile(Paths.get(task.jsonFileUri)))
    }
  }

  private def getCsvFile(uri: String): Either[Throwable, Response[String]] = {
    logger.info(s"Downloading csv file from $uri")
    implicit val backend = TryHttpURLConnectionBackend()
    sttp
      .get(uri"$uri").send().toEither
  }

  private def transformToJson(path: Path, pathToSave: String) = {
    val output = new File(s"$pathToSave")
    logger.info(s"Transforming csv from: ${path.toString} to json: ${output.toString}")
    Blocker[IO].use(blocker => {
      fs2.io.file.readAll[IO](
        path, blocker, 4096
      ).through(fs2.text.utf8Decode)
        .through(fs2.text.lines)
        .map(_.split(","))
        .map(_.asJson.noSpaces)
        .intersperse("\n")
        .through(fs2.text.utf8Encode)
        .through(fs2.io.file.writeAll(output.toPath, blocker))
        .compile.drain
    }).unsafeRunSync()
  }

  private def streamJsonFile(jsonFile: Path) = {
    logger.info(s"Streaming json from: $jsonFile")
    Blocker[IO].use(blocker => {
      fs2.io.file.readAll[IO](
        jsonFile, blocker, 4096
      ).through(fs2.text.utf8Decode)
        .through(fs2.text.lines)
        .compile.toList
    }).unsafeRunSync()
  }

  private def fileDownloader(url: String, filename: String) = {
    logger.info(s"Downloading file from $url and saving to $filename")
    new URL(url) #> new File(filename) !!
  }

}
