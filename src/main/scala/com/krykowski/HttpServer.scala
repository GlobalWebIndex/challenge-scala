package com.krykowski

import cats.effect.{ConcurrentEffect, ExitCode, IO, Timer}
import com.krykowski.api.CsvToJsonApi
import com.krykowski.config.AppConfig
import com.krykowski.repository.TaskInMemoryRepository
import com.krykowski.service.TaskServiceImpl
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

object HttpServer {
  private val logger = LoggerFactory.getLogger(getClass)

  def create(app: AppConfig)(implicit concurrentEffect: ConcurrentEffect[IO], timer: Timer[IO]): IO[ExitCode] = {
    logger.info("Starting HTTP server")

    logger.info(s"Provided app config: $app")

    val repository = new TaskInMemoryRepository
    val service = new TaskServiceImpl(repository)

    for {
      exitCode <- BlazeServerBuilder
        .apply[IO](ExecutionContext.global)
        .bindHttp(app.port, app.host)
        .withHttpApp(new CsvToJsonApi(service).routes.orNotFound).serve.compile.lastOrError
    } yield exitCode
  }
}