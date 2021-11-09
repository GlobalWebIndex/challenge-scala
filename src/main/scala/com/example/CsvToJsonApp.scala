package com.example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import java.nio.file.Files
import java.nio.file.Paths
import scala.util.Failure
import scala.util.Success

object CsvToJsonApp {

  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    import system.executionContext
    val serverHost = system.settings.config.getString("server.host")
    val serverPort = system.settings.config.getInt("server.port")

    val futureBinding = Http().newServerAt(serverHost, serverPort).bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {

    // In case output folder does not exist.
    Files.createDirectories(Paths.get("output"))

    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val taskToFileFlow = new CsvToJsonDownloader()(context.system)
      val taskActor = context.spawn(TaskActor(taskToFileFlow), "TaskActor")
      context.watch(taskActor)

      val routes = new TaskRoutes(taskActor)(context.system)
      startHttpServer(routes.taskRoutes)(context.system)

      Behaviors.empty
    }
    ActorSystem[Nothing](rootBehavior, "AkkaHttpServer")
  }
}
