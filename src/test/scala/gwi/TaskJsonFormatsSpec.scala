package gwi

import akka.http.scaladsl.model.Uri
import gwi.TaskSpec._
import io.circe.parser.parse
import io.circe.syntax._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TaskJsonFormatsSpec extends AnyFlatSpec with Matchers with TaskJsonFormats {
  "TaskJsonFormats" should "encode a scheduled task" in {
    Right((scheduled: Task).asJson) shouldBe parse(s"""
        |{
        |  "state": "SCHEDULED",
        |  "id": ${scheduled.id.value},
        |  "source": "${scheduled.source}"
        |}
        |""".stripMargin)
  }

  it should "encode a running task" in {
    val task: Task = running.stats(stats = TaskStats.overall(linesProcessed = 1000, millis = 3000))
    Right(task.asJson) shouldBe parse(s"""
        |{
        |  "state": "RUNNING",
        |  "id": ${task.id.value},
        |  "source": "${task.source}",
        |  "stats" : {
        |    "linesProcessed" : 1000,
        |    "linesProcessedPerSec" : 333.33
        |  }
        |}
        |""".stripMargin)
  }

  it should "encode a done task" in {
    val task: Task =
      running.done(result = Uri("/result/1.json"), stats = TaskStats.overall(linesProcessed = 1000, millis = 3000))

    Right(task.asJson) shouldBe parse(s"""
        |{
        |  "state": "DONE",
        |  "id": ${task.id.value},
        |  "source": "${task.source}",
        |  "result": "/result/1.json",
        |  "stats" : {
        |    "linesProcessed" : 1000,
        |    "linesProcessedPerSec" : 333.33
        |  }
        |}
        |""".stripMargin)
  }

  it should "encode a failed task" in {
    val task: Task = running.fail(cause = new RuntimeException("error message"))
    Right(task.asJson) shouldBe parse(s"""
        |{
        |  "state": "FAILED",
        |  "id": ${task.id.value},
        |  "source": "${task.source}",
        |  "cause": "error message"
        |}
        |""".stripMargin)
  }

  it should "encode a canceled task" in {
    val task: Task = running.cancel
    Right(task.asJson) shouldBe parse(s"""
        |{
        |  "state": "CANCELED",
        |  "id": ${task.id.value},
        |  "source": "${task.source}"
        |}
        |""".stripMargin)
  }
}
