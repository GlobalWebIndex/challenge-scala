package pl.datart.csvtojson.service

import akka.http.scaladsl.model.Uri
import cats.syntax.all._
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import pl.datart.csvtojson.model._
import TaskIdComp._
import cats.effect.IO
import cats.effect.kernel.Async
import pl.datart.csvtojson.util.FAdapter.FAdapterIOFGlobal.adapter._

import java.util.Date
import scala.concurrent.duration._

class StatsComposerImplTest extends AsyncFunSpec with Matchers {
  private implicit val asyncIO: Async[IO] = IO.asyncForIO

  describe("createReport") {
    it("should create a valid current report for a given running task") {
      val testedImplementation = StatsComposerImpl
      val date                 = new Date()
      val expectedStats        = TaskStats(
        linesProcessed = 0,
        avgLinesPerSec = 0,
        state = TaskState.Running,
        result = Option.empty[String]
      )
      for {
        taskId <- create[IO]
        task   <- Task(taskId, Uri(""), TaskState.Running, Option(date), None).pure[IO]
        _      <- IO.unit.delayBy(2.seconds)
        report <- testedImplementation.createReport(task).pure[IO]
      } yield report shouldBe expectedStats
    }

    it("should create a valid current report for a given completed task") {
      val testedImplementation = StatsComposerImpl
      val date                 = new Date()
      for {
        taskId        <- create[IO]
        expectedStats <- TaskStats(
                           linesProcessed = 0,
                           avgLinesPerSec = 0,
                           state = TaskState.Done,
                           result = Option(s"/file/${taskId.taskId}")
                         ).pure[IO]
        _             <- IO.unit.delayBy(2.seconds)
        task          <- Task(taskId, Uri(""), TaskState.Done, Option(date), Option(new Date())).pure[IO]
        report        <- testedImplementation.createReport(task).pure[IO]
      } yield report shouldBe expectedStats
    }
  }

}
