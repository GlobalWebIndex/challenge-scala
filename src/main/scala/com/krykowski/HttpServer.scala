package com.krykowski

import cats.effect.{ConcurrentEffect, ExitCode, IO, Timer}
import com.krykowski.api.TaskApi
import com.krykowski.config.AppConfig
import com.krykowski.service.TaskService
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

object HttpServer {
  private val logger = LoggerFactory.getLogger(getClass)

  def create(app: AppConfig, service: TaskService)(implicit concurrentEffect: ConcurrentEffect[IO], timer: Timer[IO]): IO[ExitCode] = {
    logger.info("Starting HTTP server")

    logger.info(s"Provided app config: $app")

    for {
      exitCode <- BlazeServerBuilder
        .apply[IO](ExecutionContext.global)
        .bindHttp(app.port, app.host)
        .withHttpApp(new TaskApi(service).routes.orNotFound).serve.compile.lastOrError
    } yield exitCode
  }
}