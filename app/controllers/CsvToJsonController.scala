package controllers

import conversion.ConversionService
import models.TaskCurrentState
import play.api.libs.json.Json
import play.api.libs.json.Writes.keyMapWrites
import play.api.mvc.AbstractController
import play.api.mvc.Action
import play.api.mvc.ControllerComponents

import java.io.File
import scala.concurrent.ExecutionContext

class CsvToJsonController(
    controllerComponents: ControllerComponents,
    conversionService: ConversionService
)(implicit
    ec: ExecutionContext
) extends AbstractController(controllerComponents) {

  def createTask: Action[String] = Action.async(parse.tolerantText) {
    implicit request =>
      conversionService.createTask(request.body).map(info => Ok(info.taskId))
  }
  def listTasks: Action[Unit] = Action.async(parse.empty) { implicit request =>
    conversionService.listTasks.map(tasks =>
      Ok(Json.toJson(tasks.map(taskInfoToDetails).toMap))
    )
  }
  def taskDetails(taskId: String): Action[Unit] = Action.async(parse.empty) {
    implicit request =>
      conversionService
        .getTask(taskId)
        .map(_ match {
          case None          => NotFound("No such task")
          case Some(details) => Ok(Json.toJson(taskInfoToDetails(details)._2))
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
}
