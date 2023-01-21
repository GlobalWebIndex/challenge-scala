package controllers

import com.typesafe.config.Config
import conversion.ConversionConfig
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import models.TaskId
import pool.WorkerPool
import pool.interface.TaskCurrentState
import pool.interface.TaskFinishReason
import pool.interface.TaskInfo

import akka.NotUsed
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.ContentDispositionTypes
import akka.http.scaladsl.model.headers.`Content-Disposition`
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Source
import akka.util.ByteString

import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import akka.http.scaladsl.model.ContentTypes
import io.circe.Printer

class CsvToJsonController(
    config: Config,
    workerPool: WorkerPool[ConversionConfig, TaskId, Uri, Path, ByteString]
) extends FailFastCirceSupport {
  private implicit val printer = Printer(dropNullValues = true, indent = "")

  private val pollingPeriod: FiniteDuration =
    Duration(config.getLong("csvToJson.pollingPeriodMillis"), "ms")

  def createTask(
      uri: Uri
  )(implicit ec: ExecutionContext): Route =
    complete(workerPool.createTask(uri).map(_.taskId))
  def listTasks(resultUrl: TaskId => String)(implicit
      ec: ExecutionContext
  ): Route =
    complete(
      workerPool.listTasks.map(tasks =>
        tasks
          .map(taskShortInfoToDetails(_, resultUrl))
          .toMap
      )
    )
  def taskDetails(
      taskId: TaskId,
      resultUrl: TaskId => String
  )(implicit ec: ExecutionContext): Route =
    onSuccess(workerPool.getTask(taskId))(_ match {
      case None => complete(StatusCodes.NotFound, HttpEntity("No such task"))
      case Some(info) =>
        complete(
          HttpEntity(
            ContentTypes.`text/plain(UTF-8)`,
            taskDetailsStream(info, resultUrl).map(ByteString(_))
          )
        )
    })
  def cancelTask(taskId: TaskId)(implicit
      ec: ExecutionContext
  ): Route =
    onSuccess(workerPool.cancelTask(taskId))(success =>
      if (success) complete(HttpEntity("Task cancelled"))
      else complete(StatusCodes.NotFound, HttpEntity("Task not found"))
    )

  def taskResult(
      taskId: TaskId
  )(implicit
      ec: ExecutionContext
  ): Route =
    onSuccess(workerPool.getTask(taskId))(_ match {
      case None => complete(StatusCodes.NotFound, HttpEntity("No such task"))
      case Some(details) =>
        details.state match {
          case TaskCurrentState.Finished(_, result, TaskFinishReason.Done) =>
            complete(
              StatusCodes.OK,
              Seq(
                `Content-Disposition`(
                  ContentDispositionTypes.attachment,
                  Map("filename" -> s"${taskId.id}.json")
                )
              ),
              HttpEntity(
                MediaTypes.`application/json`,
                Files.size(result),
                FileIO.fromPath(result)
              )
            )
          case _ =>
            complete(StatusCodes.BadRequest, HttpEntity("Task is not finished"))
        }
    })

  private def taskDetailsStream(
      taskInfo: TaskInfo[TaskId, Path],
      resultUrl: TaskId => String
  ): Source[String, _] =
    Source
      .tick(pollingPeriod, pollingPeriod, NotUsed)
      .mapAsync(1)(_ => workerPool.getTask(taskInfo.taskId))
      .collect({ case Some(j) => j })
      .prepend(Source.single(taskInfo))
      .takeWhile(
        _.state match {
          case TaskCurrentState.Scheduled() | TaskCurrentState.Running() => true
          case TaskCurrentState.Finished(_, _, _) => false
        },
        inclusive = true
      )
      .map(info => taskInfoToDetails(info, resultUrl).asJson.printWith(printer))
      .intersperse("\n")

}
