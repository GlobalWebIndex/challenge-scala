package com.gwi

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import com.gwi.api.TaskRouter
import com.gwi.execution.TaskExecutor
import com.gwi.service.TaskServiceImpl
import com.gwi.repository.{InMemoryTaskRepository, TaskActor, TaskActorRepository}
import com.gwi.storage.FsTaskStorage

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt

object Main {

  // TODO move those to application.conf

  // URI used for task result downloads
  val ServerUri = "http://localhost:8080"

  val ListenHost = "0.0.0.0"
  val ListenPort = 8080
  val RootDir = "/tmp/tasks"
  val ParallelTasksCount = 2

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val dispatcher: ExecutionContextExecutor = system.dispatcher
    val logger = Logging.getLogger(system, this.getClass)

    val taskStorage = new FsTaskStorage(RootDir)
    val taskActor = system.actorOf(TaskActor.props)
    val taskRepository = new TaskActorRepository(taskActor)
//    val taskRepository = new InMemoryTaskRepository()
    val taskExecutor = new TaskExecutor(taskRepository, taskStorage, ParallelTasksCount)
    val taskService = new TaskServiceImpl(taskRepository, taskStorage, taskExecutor)
    val taskRouter = new TaskRouter(taskService)

    Http()
      .newServerAt(ListenHost, ListenPort)
      .bind(taskRouter.routes)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))
      .foreach(_ => logger.info(s"Server started at $ListenHost:$ListenPort"))
  }

}
