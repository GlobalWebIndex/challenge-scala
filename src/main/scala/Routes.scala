import cats.effect._
import cats.effect.std._
import io.circe.syntax._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl._
import fs2.Stream
import fs2.concurrent.SignallingRef
import org.http4s.headers.`Content-Type`

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.UUID.randomUUID
import scala.collection.concurrent
import scala.concurrent.duration._

object Routes {

  import TaskStatus._
  import TaskModel._

  def taskRoutes(taskMap: concurrent.Map[String, Task],
                 schedulerQueue: Queue[IO, String]): HttpRoutes[IO] = {
    val dsl = Http4sDsl[IO]
    import dsl._
    HttpRoutes.of[IO] {
      case GET -> Root / "task" =>
        val taskOutput = taskMap.values.map(task => task.toOutput).toList
        for {
          res <- Ok(taskOutput.asJson)
        } yield res

      case req@POST -> Root / "task" =>
        for {
          taskInput <- req.as[TaskInput]
          cancelSwitch <- SignallingRef[IO, Boolean](false)
          task = Task(randomUUID().toString,
            taskInput.url, new AtomicReference[Status](SCHEDULED),
            new AtomicInteger(0),
            new AtomicInteger(0),
            new AtomicInteger(0),
            cancelSwitch
          )
          _ = taskMap.put(task.id, task)
          _ <- schedulerQueue.offer(task.id)
          taskOutput = task.toOutput
          res <- Ok(taskOutput.asJson)
        } yield res

      case GET -> Root / "task" / UUIDVar(taskId) =>
        taskMap.get(taskId.toString) match {
          case Some(task) =>
            for {
              hasEnded <- task.cancel.get
              res <- if (hasEnded)
                Ok(task.toOutput.asJson)
              else
                Ok(Stream.awakeEvery[IO](2.second)
                  .map(_ => task.toOutput.asJson.toString() + "\n")
                  .interruptWhen(task.cancel))
            } yield res


          case None => NotFound("Not Found")
        }

      case req@GET -> Root / "task" / UUIDVar(taskId) / "json" =>
        StaticFile.fromPath(fs2.io.file.Path(s"jsons/$taskId.json"), Some(req))
          .getOrElseF(NotFound()).map(_.withContentType(`Content-Type`(MediaType.application.json)))

      case DELETE -> Root / "task" / UUIDVar(taskId) =>
        taskMap.get(taskId.toString) match {
          case Some(task) =>
            for {
              _ <- task.cancel.set(true) >>
                IO(task.status.set(CANCELED)) >>
                IO.println(s"canceled task ${task.id}")
              res <- Ok(task.toOutput.asJson)
            } yield res

          case None => NotFound("Not Found")
        }
    }
  }
}
