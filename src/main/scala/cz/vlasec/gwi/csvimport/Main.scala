package cz.vlasec.gwi.csvimport

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import cz.vlasec.gwi.csvimport.Routes._
import cz.vlasec.gwi.csvimport.Sourceror.SourcerorCommand
import cz.vlasec.gwi.csvimport.task.Overseer.{OverseerCommand => TaskOverseerCommand}
import cz.vlasec.gwi.csvimport.task.{Overseer => TaskOverseer, Service => TaskService}
import cz.vlasec.gwi.csvimport.task.Service.{ServiceCommand => TaskServiceCommand}

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "my-system")

    val taskServiceRef = system.systemActorOf[TaskServiceCommand](TaskService(), "task-service")
    val sourcerorRef = system.systemActorOf[SourcerorCommand](Sourceror(), "sourceror")
    system.systemActorOf[TaskOverseerCommand](TaskOverseer(workerCount = 2, taskServiceRef, sourcerorRef), "task-overseer")


    val bindingFuture = Http().newServerAt("localhost", 8080).bind(routes(taskServiceRef)(system.scheduler))

    // Somewhat useful e.g. for runs from IDE. It is taken from a Hello World snippet I found somewhere.
    println(s"Server now online. Please navigate to http://localhost:8080/task\nPress RETURN to stop...")
    StdIn.readLine()

    implicit val executionContext: ExecutionContextExecutor = system.executionContext
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}

