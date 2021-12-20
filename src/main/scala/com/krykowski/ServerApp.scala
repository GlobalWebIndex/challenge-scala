package com.krykowski

import cats.effect.{ExitCode, IO, IOApp}
import com.krykowski.config.Config
import com.krykowski.repository.TaskInMemoryRepository
import com.krykowski.service.TaskServiceImpl

object ServerApp extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val appConf = Config.appConfig

    val repository = new TaskInMemoryRepository
    val service = new TaskServiceImpl(repository)

    HttpServer.create(appConf, service)
  }
}
