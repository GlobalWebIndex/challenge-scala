import cats.effect._
import cats.effect.std._
import org.http4s.ember.client._
import fs2.io.file.{Files, Flags, Path}
import fs2.text
import fs2.Stream
import fs2.data.csv._
import fs2.data.text.utf8._
import io.circe.syntax._
import org.http4s.{Method, Request, Uri}

import scala.collection.concurrent

object Workers {
  import TaskModel._
  import TaskStatus._
  def scheduler(taskMap: concurrent.Map[String, Task],
                schedulerQueue: Queue[IO, String],
                workerQueue: Queue[IO, String]): IO[Nothing] = {
    IO.println("Starting scheduler ") >>
      schedulerQueue.take.flatMap { tid =>
        taskMap.get(tid) match {
          case Some(task) =>
            if (task.status.get() == SCHEDULED) workerQueue.offer(tid)
            else IO.unit
          case None =>
            IO.unit
        }
      }.foreverM
  }

  def worker(taskMap: concurrent.Map[String, Task], workerId: Int, workerQueue: Queue[IO, String]): IO[Nothing] = {
    IO.println(s"Starting worker $workerId") >>
      workerQueue
        .take
        .flatTap(tid => IO.println(s"Running $tid from $workerId"))
        .flatTap(tid => taskMap.get(tid) match {
          case Some(task) =>
            EmberClientBuilder.default[IO].build.use { client =>
              val req = Request[IO](Method.GET, Uri.fromString(task.url).toOption.get)
              val httpStream = for {
                _ <- Stream.eval(IO(if (task.status.get() != CANCELED && task.status.get() != FAILED) task.status.set(RUNNING)))
                clientStream <- client
                  .stream(req)
                  .flatMap(_.body)
                  .through(decodeUsingHeaders[CsvRow[String]]())
                  .interruptWhen(task.cancel)
                  .evalTap { _ =>
                    IO(task.linesProcessed.incrementAndGet()) >>
                      IO(task.linesPerSecond.incrementAndGet())
                  }
                  .map(x => x.toMap)
                  .map(x => x.asJson.noSpaces + "\n")
                  .through(text.utf8.encode)
                  .through(Files[IO].writeAll(Path(s"jsons/${task.id}.json"), Flags.Write))
              } yield clientStream

              httpStream
                .handleErrorWith(_ => Stream.eval(IO(task.status.set(FAILED))))
                .compile
                .drain
                .flatTap(_ => IO(if (task.status.get() != CANCELED && task.status.get() != FAILED) task.status.set(DONE)))
                .flatTap(_ => task.cancel.set(true))
            }
          case None => IO.unit
        })
        .flatTap(tid => IO.println(s"Ended $tid from $workerId"))
        .foreverM
  }
}
