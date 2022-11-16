package com.github.maenolis.server

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.github.maenolis.service.TaskService
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database

import scala.io.StdIn

//#main-class
object Server {
  //#start-http-server
  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    import system.executionContext

    val futureBinding = Http().newServerAt("localhost", 8080).bind(routes)

    println(s"Server now online. Please navigate to http://localhost:8080/hello\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    futureBinding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

  //#start-http-server
  def main(args: Array[String]): Unit = {
    //#server-bootstrapping
    implicit val system = ActorSystem(Behaviors.empty, "my-system")
    val db: JdbcBackend.Database = Database.forConfig("db")
    val taskService = new TaskService(db)

    startHttpServer(new TaskRoutes(taskService).allRoutes())(system)

    //#server-bootstrapping
  }
}
