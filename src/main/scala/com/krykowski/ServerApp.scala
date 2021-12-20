package com.krykowski

import cats.effect.{ExitCode, IO, IOApp}
import com.krykowski.config.Config

object ServerApp extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val appConf = Config.appConfig

    HttpServer.create(appConf)
  }
}
