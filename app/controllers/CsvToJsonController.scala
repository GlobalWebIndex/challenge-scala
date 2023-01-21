package controllers

import com.typesafe.config.Config
import conversion.ConversionConfig
import models.TaskId
import pool.WorkerPool
import pool.interface.{TaskCurrentState, TaskInfo}

import akka.NotUsed
import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.json.Json
import play.api.libs.json.Writes.keyMapWrites
import play.api.mvc.{
  AbstractController,
  Action,
  ControllerComponents,
  RequestHeader
}
import pool.interface.TaskFinishReason

import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}

class CsvToJsonController(
    config: Config,
    controllerComponents: ControllerComponents,
    conversionService: WorkerPool[
      ConversionConfig,
      TaskId,
      Uri,
      Path,
      ByteString
    ]
)(implicit
    ec: ExecutionContext
) extends AbstractController(controllerComponents) {
  val pollingPeriod: FiniteDuration =
    Duration(config.getLong("csvToJson.pollingPeriodMillis"), "ms")

  def createTask: Action[Uri] = Action.async(parse.tolerantText.map(Uri(_))) {
    implicit request =>
      conversionService
        .createTask(request.body)
        .map(info => Ok(info.taskId))
  }
  def listTasks: Action[Unit] = Action.async(parse.empty) { implicit request =>
    conversionService.listTasks.map(tasks =>
      Ok(Json.toJson(tasks.map(taskShortInfoToDetails).toMap))
    )
  }
  def taskDetails(taskId: TaskId): Action[Unit] =
    Action.async(parse.empty) { implicit request =>
      conversionService
        .getTask(taskId)
        .map(_ match {
          case None       => NotFound("No such task")
          case Some(info) => Ok.chunked(taskDetailsStream(info), None)
        })
    }
  def cancelTask(taskId: TaskId): Action[Unit] = Action.async(parse.empty) {
    implicit request =>
      conversionService
        .cancelTask(taskId)
        .map(success =>
          if (success) Ok("Task cancelled") else NotFound("Task not found")
        )
  }

  def taskResult(taskId: TaskId): Action[Unit] = Action.async(parse.empty) {
    implicit request =>
      conversionService
        .getTask(taskId)
        .map(_ match {
          case None => NotFound("No such task")
          case Some(details) =>
            details.state match {
              case TaskCurrentState
                    .Finished(_, result, TaskFinishReason.Done) =>
                Ok.sendPath(
                  result,
                  fileName = _ => Some(s"${taskId.id}.json")
                )
              case _ => BadRequest("Task is not finished")
            }
        })
  }

  private def taskDetailsStream(
      taskInfo: TaskInfo[TaskId, Path]
  )(implicit request: RequestHeader) =
    Source
      .tick(pollingPeriod, pollingPeriod, NotUsed)
      .mapAsync(1)(_ => conversionService.getTask(taskInfo.taskId))
      .collect({ case Some(j) => j })
      .prepend(Source.single(taskInfo))
      .takeWhile(
        _.state match {
          case TaskCurrentState.Scheduled() | TaskCurrentState.Running() => true
          case TaskCurrentState.Finished(_, _, _) => false
        },
        inclusive = true
      )
      .map(info => Json.stringify(Json.toJson(taskInfoToDetails(info))))
      .intersperse("\n")

}
