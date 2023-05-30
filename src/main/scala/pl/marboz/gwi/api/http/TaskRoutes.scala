package pl.marboz.gwi.api.http

import java.util.UUID
import scala.concurrent.duration.DurationInt
import cats.effect.Async
import cats.effect.kernel.Concurrent
import cats.implicits._
import fs2.Stream
import org.http4s.{Header, HttpRoutes, MediaType}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import pl.marboz.gwi.api.request.TaskRequest
import pl.marboz.gwi.api.response.{TaskResponse, TaskStatusResponse}
import pl.marboz.gwi.application.algebra.TaskAlgebra
import pl.marboz.gwi.application.algebra.TaskAlgebra.{CreateTask, TaskStatus}
import pl.marboz.gwi.application.model.TaskStatus.{RUNNING, SCHEDULED}

import java.nio.file.Paths

/** @author
  *   <a href="mailto:marboz85@gmail.com">Marcin Bozek</a> Date: 20.05.2023 13:52
  */
object TaskRoutes {

  def routes[F[_]: Concurrent: Async](taskAlgebra: TaskAlgebra[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case req @ POST -> Root / "task" =>
        for {
          taskRequest <- req.as[TaskRequest]
          res         <- taskAlgebra.createTask(CreateTask(taskRequest.uri))
          resp        <- Ok(TaskResponse(res.status))
        } yield resp
      case GET -> Root / "task" =>
        for {
          resp <- Ok(taskAlgebra.getTaskList())
        } yield resp
      case GET -> Root / "task" / uuid =>
        val taskStream: Stream[F, TaskStatus] =
          Stream.awakeEvery[F](2.seconds).evalMap(_ => taskAlgebra.getTaskStatus(UUID.fromString(uuid))).takeWhile(taskStatus => taskStatus.taskStatus == SCHEDULED.toString || taskStatus.taskStatus == RUNNING.toString)
        for {
          resp <- Ok(taskStream.map(taskStatus => TaskStatusResponse(taskStatus)))
        } yield resp
      case DELETE -> Root / "task" / uuid =>
        for {
          greeting <- taskAlgebra.deleteTask(uuid)
          resp     <- Ok(greeting.status)
        } yield resp
      case GET -> Root / "task" / uuid / "json-file" =>
        val fileStream = taskAlgebra.getFile(uuid)
        Ok(fileStream, `Content-Type`(MediaType.application.`octet-stream`))
          .map(_.withHeaders(Header("Content-Disposition", s"attachment; filename=${Paths.get(uuid).getFileName}")))
    }
  }

}
