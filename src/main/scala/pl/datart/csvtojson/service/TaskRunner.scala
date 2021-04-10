package pl.datart.csvtojson.service

import akka.http.scaladsl.model.Uri
import better.files.File
import cats.effect._
import cats.effect.kernel.Resource.ExitCase
import cats.effect.std._
import cats.syntax.all._
import fs2._
import fs2.concurrent.{Signal, SignallingRef}
import fs2.data.csv._
import fs2.io.file.Files
import pl.datart.csvtojson.model._
import spray.json.DefaultJsonProtocol._
import spray.json._

import java.net.URL
import java.nio.file.Paths
import java.nio.file.StandardOpenOption._

trait TaskRunner[F[_]] {
  def run(taskId: TaskId, uri: Uri, signal: SignallingRef[F, Boolean]): F[Unit]
}

@SuppressWarnings(
  Array("org.wartremover.warts.Any", "org.wartremover.warts.ImplicitParameter", "org.wartremover.warts.Nothing")
)
class TaskRunnerImpl[F[_]](taskService: TaskService[F], semaphore: Semaphore[F])(implicit
    async: Async[F]
) extends TaskRunner[F] {
  override def run(taskId: TaskId, uri: Uri, signal: SignallingRef[F, Boolean]): F[Unit] = {
    getAndSave(taskId, uri, signal)
  }

  private def getAndSave(taskId: TaskId, uri: Uri, signal: SignallingRef[F, Boolean]): F[Unit] = {
    val outputFile = File(Paths.get(System.getProperty("java.io.tmpdir"))) / s"${taskId.taskId}.json"

    {
      for {
        _ <- semaphore.acquire
        _ <- taskService.updateTask(taskId, TaskState.Running)
        _ <- prepareStream(taskId, uri, outputFile, signal).drain
      } yield ()
    }.onError {
      case _ =>
        for {
          _ <- outputFile.delete(swallowIOExceptions = true).pure
        } yield ()
    }
  }

  private def prepareStream(taskId: TaskId, uri: Uri, outputFile: File, signal: Signal[F, Boolean]) = {
    val arrayStart = "[\n"
    val arrayEnd   = "\n]"
    val chunkSize  = 4096

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
      .interruptWhen(signal)
      .onFinalizeCase {
        case ExitCase.Succeeded  =>
          taskService
            .updateTask(taskId, TaskState.Done)
            .flatMap(_ => semaphore.release)
        case ExitCase.Errored(_) =>
          taskService
            .updateTask(taskId, TaskState.Failed)
            .flatMap(_ => semaphore.release)
        case ExitCase.Canceled   =>
          taskService
            .updateTask(taskId, TaskState.Canceled)
            .flatMap(_ => semaphore.release)
      }
      .compile
  }

  private def cleanseCsvData(csvData: Map[String, String]): Map[String, String] =
    csvData
      .filterNot { case (key, _) => key.isEmpty }
      .view
      .toMap

  private def toJson(map: Map[String, String])(implicit jsWriter: JsonWriter[Map[String, String]]): JsValue =
    jsWriter.write(map)
}
