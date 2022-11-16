package com.github.maenolis.server

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, NoContent}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.github.maenolis.model.{JsonFormats, Task, TaskDto, TaskList}
import com.github.maenolis.service.TaskService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


class TaskRoutes(taskService: TaskService)(implicit val system: ActorSystem[_]) {

  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("csv-app.routes.ask-timeout"))

  implicit val ec: ExecutionContext = system.executionContext

  private def getTasks(): Future[TaskList] = taskService.getTasks().map(tasks => TaskList(tasks))

  private def getTask(id: Long): Future[Option[Task]] = taskService.getTask(id)

  private def cancelTask(id: Long): Future[Int] = taskService.cancel(id)

  private def insertTask(taskDto: TaskDto): Future[Int] = taskService.insert(taskDto)

  def getTasksRoute: Route = get {
    onComplete(getTasks()) {
      case Success(tasks) => complete(tasks)
      case Failure(ex) => complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
    }
  }

  def getTaskRoute(id: Long): Route = get {
    onComplete(getTask(id)) {
      case Success(task) => complete(task)
      case Failure(ex) => complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
    }
  }

  def postTaskRoute: Route = post {
    decodeRequest {
      entity(as[TaskDto]) { taskDto => onComplete(insertTask(taskDto)) {
        case Success(_) => complete(NoContent)
        case Failure(ex) => complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
      } }
    }
  }

  def cancelTaskRoute(id: Long): Route = delete {
    onComplete(cancelTask(id)) {
      case Success(_) => complete(NoContent)
      case Failure(ex) => complete(InternalServerError, s"An error occurred: ${ex.getMessage}")
    }
  }


  def allRoutes(): Route =
      path("tasks")(getTasksRoute) ~
      path("tasks")(postTaskRoute) ~
      pathPrefix("tasks" / LongNumber)(getTaskRoute) ~
      pathPrefix("tasks" / LongNumber)(cancelTaskRoute)

}
