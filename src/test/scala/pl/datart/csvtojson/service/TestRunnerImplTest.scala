package pl.datart.csvtojson.service

import akka.http.scaladsl.model.Uri
import better.files.File
import cats.syntax.all._
import cats.effect.IO
import cats.effect.kernel.Async
import cats.effect.std.Semaphore
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import pl.datart.csvtojson.model._
import pl.datart.csvtojson.service.TaskService.StatsSource
import pl.datart.csvtojson.util.FAdapter.FAdapterIOFGlobal.adapter._

import java.nio.file.Paths
import java.util.UUID
import scala.io.Source

class TestRunnerImplTest extends AsyncFunSpec with Matchers {
  private implicit val asyncIO: Async[IO] = IO.asyncForIO
  describe("run") {
    it("should run correctly the task") {
      def mockedTaskService(task: Task): TaskService[IO] =
        new TaskService[IO] {
          override def addTask(task: Task): IO[Unit]                                  = IO.unit
          override def getTasks: IO[Iterable[TaskId]]                                 = IO(Iterable.empty[TaskId])
          override def getTask(taskId: TaskId): IO[Option[Task]]                      = IO(Option(task))
          override def updateTask(taskId: TaskId, state: TaskState): IO[Option[Task]] = IO.pure(Option.empty[Task])
          override def getStats(taskId: TaskId): IO[Option[StatsSource]]              = IO(None)
        }
      for {
        semaphore <- Semaphore[IO](2)
        taskId              <- TaskId(UUID.randomUUID().toString).pure[IO]
        uri                 <- Uri(s"file://${Source.getClass.getResource("/example_file.csv").getPath}").pure[IO]
        task                <- Task(taskId, uri, TaskState.Scheduled, None, None).pure[IO]
        testedImplementation = new TaskRunnerImpl[IO](mockedTaskService(task), semaphore)
        _                   <- testedImplementation.run(taskId, uri)
        outputFile          <- (File(Paths.get(System.getProperty("java.io.tmpdir"))) / s"${taskId.taskId}.json").pure[IO]
      } yield outputFile.contentAsString shouldBe
        s"""[
         |{"a":"1","b":"2","d":"4"}
         |{"a":"5","b":"6","d":"8"}
         |]""".stripMargin
    }
  }
}
