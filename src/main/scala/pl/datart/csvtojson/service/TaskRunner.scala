package pl.datart.csvtojson.service

import akka.http.scaladsl.model.Uri
import better.files.File
import cats.effect._
import cats.effect.kernel.Resource.ExitCase
import cats.effect.std._
import cats.syntax.all._
import fs2._
import fs2.data.csv._
import fs2.io.file.Files
import pl.datart.csvtojson.model._
import spray.json.DefaultJsonProtocol._
import spray.json._

import java.net.URL
import java.nio.file.Paths
import java.nio.file.StandardOpenOption._
import scala.concurrent.duration._

trait TaskRunner[F[_]] {
  def run(taskId: TaskId, uri: Uri): F[Unit]
}

@SuppressWarnings(
  Array("org.wartremover.warts.Any", "org.wartremover.warts.ImplicitParameter", "org.wartremover.warts.Nothing")
)
class TaskRunnerImpl[F[_]](taskService: TaskService[F], semaphore: Semaphore[F])(implicit
    async: Async[F]
) extends TaskRunner[F] {
  override def run(taskId: TaskId, uri: Uri): F[Unit] = {
    getAndSave(taskId, uri)
  }

  private def getAndSave(taskId: TaskId, uri: Uri): F[Unit] = {
    for {
      _          <- semaphore.acquire
      taskOption <- taskService.getTask(taskId)
      _          <- taskOption.fold(semaphore.release) { task =>
                      task.state match {
                        case TaskState.Scheduled =>
                          taskService
                            .updateTask(taskId, TaskState.Running)
                            .flatMap(_ => prepareStream(taskId, uri).drain)
                        case _                   =>
                          semaphore.release
                      }
                    }
    } yield ()
  }

  private def prepareStream(taskId: TaskId, uri: Uri) = {
    val outputFile = File(Paths.get(System.getProperty("java.io.tmpdir"))) / s"${taskId.taskId}.json"

    val arrayStart = "[\n"
    val arrayEnd   = "\n]"
    val chunkSize  = 4096

    val switch = killSwitch(taskId)

    fs2.io
      .readInputStream(
        async.interruptible(many = false)(new URL(uri.toString()).openConnection.getInputStream),
        chunkSize
      )
      .map(_.toChar)
      .through(rows[F, Char]())
      .through(headers[F, String])
      .map(_.toMap)
      .map(cleanseCsvData)
      .map(toJson)
      .map(_.compactPrint)
      .map { jsonString =>
        Metrics.metrics.counter(taskId.taskId).inc()
        jsonString
      }
      .intersperse("\n")
      .cons1(arrayStart)
      .append(Stream.eval(arrayEnd.pure))
      .through(text.utf8Encode)
      .through(Files[F].writeAll(outputFile.path, Seq(WRITE, APPEND, CREATE)))
      .interruptWhen(switch)
      .onFinalizeCase {
        case ExitCase.Succeeded  =>
          successFinalizer(taskId, outputFile)
        case ExitCase.Errored(_) =>
          ceFinalizer(taskId, outputFile, TaskState.Failed)
        case ExitCase.Canceled   =>
          ceFinalizer(taskId, outputFile, TaskState.Canceled)
      }
      .compile
  }

  private def successFinalizer(taskId: TaskId, outputFile: File) = {
    for {
      taskOption <- taskService.getTask(taskId)
      _          <- taskOption.fold(async.unit) { task =>
                      task.state match {
                        case TaskState.Running                     =>
                          taskService.updateTask(taskId, TaskState.Done).map(_ => (()))
                        case TaskState.Canceled | TaskState.Failed =>
                          outputFile.delete(swallowIOExceptions = true).pure.map(_ => (()))
                        case _                                     => async.unit
                      }
                    }
      _          <- semaphore.release
    } yield ()
  }

  private def ceFinalizer(taskId: TaskId, outputFile: File, state: TaskState) = {
    taskService
      .updateTask(taskId, state)
      .flatMap(_ => semaphore.release)
      .flatMap(_ => outputFile.delete(swallowIOExceptions = true).pure.map(_ => (())))
  }

  private def killSwitch(taskId: TaskId): Stream[F, Boolean] = {
    Stream
      .every(1.second)
      .flatMap(_ => Stream.eval(taskService.getTask(taskId).map(_.fold(true)(_.isCanceledOrFailed))))
  }

  private def cleanseCsvData(csvData: Map[String, String]): Map[String, String] =
    csvData
      .filterNot { case (key, _) => key.isEmpty }
      .view
      .toMap

  private def toJson(map: Map[String, String])(implicit jsWriter: JsonWriter[Map[String, String]]): JsValue =
    jsWriter.write(map)
}
