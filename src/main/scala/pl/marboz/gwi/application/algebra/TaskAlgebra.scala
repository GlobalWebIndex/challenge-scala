package pl.marboz.gwi.application.algebra

import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import cats.effect.{Async, Concurrent, Ref}
import cats.implicits._
import fs2.text
import fs2.text.{lines, utf8Encode}
import io.circe.syntax.EncoderOps
import org.http4s.{Method, Request, Uri}
import org.http4s.client.Client
import org.http4s.implicits.http4sLiteralsSyntax
import pl.marboz.gwi.api.response.{TaskDetailsResponse, TaskListResponse}
import pl.marboz.gwi.application.algebra.TaskAlgebra.{CreateTask, CreateTaskResult, DeleteTaskResult, TaskStatus}
import pl.marboz.gwi.application.model.Task
import pl.marboz.gwi.application.model.TaskStatus.{DONE, RUNNING, SCHEDULED, UNKNOWN}

/** @author
  *   <a href="mailto:marboz85@gmail.com">Marcin Bozek</a> Date: 20.05.2023 19:16
  */

trait TaskAlgebra[F[_]] {
  def getTaskStatus(uuid: UUID): F[TaskStatus]

  def createTask(createTask: CreateTask): F[CreateTaskResult]
  def deleteTask(uuid: String): F[DeleteTaskResult]

  def getTaskList(): F[TaskListResponse]

  def getFile(uuid: String): fs2.Stream[F, Byte]
}
object TaskAlgebra {

  case class CreateTask(uri: Uri)
  case class CreateTaskResult(status: String) extends AnyVal
  case class DeleteTaskResult(status: String) extends AnyVal

  case class TaskStatus(linesProcessedAmount: Int, totalLinesAmount: Int, taskStatus: String, uri: String)

  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))

  def impl[F[_]: Concurrent: Async](
    taskState: Ref[F, Map[UUID, Task]],
    client: Client[F]
  ): TaskAlgebra[F] = {
    new TaskAlgebra[F] {
      override def createTask(ct: CreateTask): F[CreateTaskResult] = {
        val uuid = UUID.randomUUID()
        val task = Task(0, 0, uuid, SCHEDULED, Some(ct.uri))
        for {
          _ <- taskState.update(_ + (uuid -> task))
          _ <- runTask(task)
        } yield CreateTaskResult(uuid.toString)
      }

      override def deleteTask(uuid: String): F[DeleteTaskResult] = {
        for {
          map <- taskState.get
          _   <- taskState.update(_ => map.removed(UUID.fromString(uuid)))
        } yield DeleteTaskResult("DONE")
      }

      override def getTaskStatus(uuid: UUID): F[TaskStatus] = {
        for {
          taskMap <- taskState.get
          task           = taskMap.get(uuid)
          status         = task.map(t => t.status.toString).getOrElse(UNKNOWN.toString)
          linesProcessed = task.map(t => t.linesProcessedAmount).getOrElse(0)
          totalLines     = task.map(t => t.totalLinesAmount).getOrElse(0)
        } yield TaskStatus(linesProcessed, totalLines, status, s"http://localhost:8080/task/${uuid}/json-file")
      }

      override def getTaskList(): F[TaskListResponse] = {
        for {
          map <- taskState.get
          list = map.values.map(t => TaskDetailsResponse(t)).toList
          res  = new TaskListResponse(list)
        } yield res
      }

      override def getFile(uuid: String): fs2.Stream[F, Byte] = {
        fs2.io.file.readAll[F](Paths.get(uuid.toString), 4096)
      }

      private def processLine(headers: Array[String], l: String, uuid: UUID): F[String] = {
        for {
          map <- taskState.get
          task           = map.get(uuid)
          status         = task.map(t => t.status).getOrElse(UNKNOWN)
          totalLines     = task.map(t => t.totalLinesAmount).getOrElse(0)
          linesProcessed = task.map(t => t.linesProcessedAmount).getOrElse(0)
          uri            = task.map(t => t.uri).getOrElse(Option.empty)
          newMap         = map + (uuid -> Task(linesProcessed + 1, totalLines, uuid, status, uri))
          _ <- taskState.update(_ => newMap)
        } yield headers.zip(l.split(",")).toMap.asJson.noSpaces
      }

      private def runTask(task: Task)(implicit ec: ExecutionContext): F[CreateTaskResult] = {
        Async[F].evalOn(runRunnable(task), ec)
      }

      private def runRunnable(task: Task): F[CreateTaskResult] = {
        val uuid    = task.uuid
        val uri     = task.uri
        val request = Request[F](Method.GET, uri.getOrElse(uri""))
        val path    = Paths.get(task.uuid.toString)

        val linesStream = streamFrom(request).through(text.utf8Decode).through(lines)
        for {
          linesCnt <- linesStream.fold(0)((cnt, _) => cnt + 1).compile.lastOrError
          _        <- taskState.update(_ + (uuid -> Task(0, linesCnt, uuid, RUNNING, uri)))
          headers  <- linesStream.take(1).compile.lastOrError.map(_.split(","))
          _        <- taskState.update(_ + (uuid -> Task(1, linesCnt, uuid, RUNNING, uri)))
          rrr <- linesStream
            .drop(1)
            .flatMap(l => fs2.Stream.eval(processLine(headers, l, uuid)))
            .through(utf8Encode)
            .through(fs2.io.file.writeAll(Paths.get(task.uuid.toString)))
            .compile
            .drain
            .as(CreateTaskResult(path.toString))
          _ <- taskState.update(_ + (uuid -> Task(linesCnt, linesCnt, uuid, DONE, uri)))
        } yield rrr
      }

      private def streamFrom(request: Request[F]) = {
        client
          .stream(request)
          .flatMap { res =>
            res.body
          }
      }

    }
  }
}
