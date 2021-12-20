package com.krykowski.api

import cats.effect.IO
import com.krykowski.model.{CsvFileLocation, Status, TaskNotFoundError}
import com.krykowski.service.TaskService
import io.circe.{Decoder, Encoder}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.circe._

class CsvToJsonApi(service: TaskService) extends Http4sDsl[IO] {
  private val logger = LoggerFactory.getLogger(getClass)

  private implicit val encodeImportance: Encoder[Status] =
    Encoder.encodeString.contramap[Status](_.value)

  private implicit val decodeImportance: Decoder[Status] =
    Decoder.decodeString.map[Status](Status.unsafeFromString)

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "task" / LongVar(id) / "json" =>
      logger.info(s"Started getting json for id: $id")
      service.getJson(id).flatMap {
        case Left(TaskNotFoundError) =>
          logger.info(s"Task with id $id not found")
          NotFound()
        case Right(jsonValue) =>
          logger.info(s"Found json for $id. Started processing")
          Ok(jsonValue)
      }

    case GET -> Root / "task" =>
      logger.info(s"Returning all tasks")
      Ok(service.getAllTasks())

    case req@POST -> Root / "task" =>
      logger.info("Creating new task")
      for {
        task <- req.decodeJson[CsvFileLocation]
        idOfCreatedTask <- service.createTask(task)
        response <- Created(idOfCreatedTask)
      } yield response

    case DELETE -> Root / "task" / LongVar(id) =>
      logger.info(s"Started cancelling task with id: $id")
      service.cancelTask(id).flatMap {
        case Left(TaskNotFoundError) =>
          logger.info(s"Task with id $id not found")
          NotFound()
        case Right(_) =>
          logger.info(s"Task with id $id cancelled")
          NoContent()
      }
  }
}
