package com.github.maenolis.server

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.github.maenolis.http.HttpClientRequestService
import com.github.maenolis.service.TaskService
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.io.StdIn

object Server {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "my-system")
    implicit val ec: ExecutionContextExecutor = system.executionContext
    val db: JdbcBackend.Database = Database.forConfig("db")
    val taskService = new TaskService(new HttpClientRequestService, db)

    val futureBinding = Http()
      .newServerAt("localhost", 8080)
      .bind(new TaskRoutes(taskService).allRoutes())

    Await.result(Future.never, Duration.Inf)

    // let it run until user presses return
    futureBinding
      .flatMap(_.unbind())
      .onComplete(_ => {
        db.close()
        system.terminate()
      })
  }
}
