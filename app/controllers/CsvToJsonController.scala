package controllers

import akka.NotUsed
import akka.stream.scaladsl.Source
import conversion.ConversionService
import models.TaskCurrentState
import models.TaskInfo
import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.json.Writes.keyMapWrites
import play.api.mvc.AbstractController
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import play.api.mvc.RequestHeader

import java.io.File
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

class CsvToJsonController(
    config: Configuration,
    controllerComponents: ControllerComponents,
    conversionService: ConversionService
)(implicit
    ec: ExecutionContext
) extends AbstractController(controllerComponents) {
  val pollingPeriod =
    Duration(config.get[Long]("csvToJson.pollingPeriodMillis"), "ms")

  def createTask: Action[String] = Action.async(parse.tolerantText) {
    implicit request =>
      conversionService.createTask(request.body).map(info => Ok(info.taskId))
  }
  def listTasks: Action[Unit] = Action.async(parse.empty) { implicit request =>
    conversionService.listTasks.map(tasks =>
      Ok(Json.toJson(tasks.map(taskShortInfoToDetails).toMap))
    )
  }
  def taskDetails(taskId: String): Action[Unit] =
    Action.async(parse.empty) { implicit request =>
      conversionService
        .getTask(taskId)
        .map(_ match {
          case None       => NotFound("No such task")
          case Some(info) => Ok.chunked(taskDetailsStream(info), None)
        })
    }
  def cancelTask(taskId: String): Action[Unit] = Action.async(parse.empty) {
    implicit request =>
      conversionService
        .cancelTask(taskId)
        .map(success =>
          if (success) Ok("Task cancelled") else NotFound("Task not found")
        )
  }

  def taskResult(name: String): Action[Unit] = Action.async(parse.empty) {
    implicit request =>
      conversionService
        .getTask(name)
        .map(_ match {
          case None => NotFound("No such task")
          case Some(details) =>
            details.state match {
              case TaskCurrentState.Done(_, result) =>
                Ok.sendFile(
                  result,
                  fileName = (_: File) => Some(s"$name.json")
                )
              case _ => BadRequest("Task is not finished")
            }
        })
  }

  private def taskDetailsStream(
      taskInfo: TaskInfo
  )(implicit request: RequestHeader) =
    Source
      .tick(pollingPeriod, pollingPeriod, NotUsed)
      .mapAsync(1)(_ => conversionService.getTask(taskInfo.taskId))
      .collect({ case Some(j) => j })
      .prepend(Source.single(taskInfo))
      .takeWhile(
        _.state match {
          case TaskCurrentState.Done(_, _) => false
          case _                           => true
        },
        inclusive = true
      )
      .map(info => Json.stringify(Json.toJson(taskInfoToDetails(info))))
      .intersperse("\n")

}
