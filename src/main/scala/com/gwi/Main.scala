package com.gwi

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.gwi.route.TaskRouter
import com.gwi.service.TaskServiceImpl
import com.gwi.repository.InMemoryTaskRepository
import com.gwi.storage.FsTaskStorage

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt

object Main {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val dispatcher: ExecutionContextExecutor = system.dispatcher

    val host = "0.0.0.0"
    val port = 8080
    val rootDir = "/tmp/tasks"

    val taskStorage = new FsTaskStorage(rootDir)
    val taskRepository = new InMemoryTaskRepository()
    val taskService = new TaskServiceImpl(taskRepository, taskStorage)
    val taskRouter = new TaskRouter(taskService)

    Http()
      .newServerAt(host, port)
      .bind(taskRouter.routes)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))
      .foreach(_ => println(s"Server started at $host:$port"))
  }

}
