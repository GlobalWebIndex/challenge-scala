package gwi

import akka.http.scaladsl.model.Uri
import gwi.TaskSpec._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

object TaskSpec {
  val id1: TaskId = TaskId(1)

  val source: Uri = Uri("/sample.csv")
  val result: Uri = Uri("/sample.json")

  val stats: TaskStats = TaskStats(linesProcessed = 1, linesProcessedPerSec = 1)

  val scheduled: ScheduledTask = ScheduledTask(id1, source)
  val running: RunningTask = RunningTask(id1, source, None)
}

class TaskSpec extends AnyFlatSpec with Matchers {
  "Task" should "check if it is in a terminal state" in {
    Task.isTerminal(scheduled) shouldBe false
    Task.isTerminal(running) shouldBe false
    Task.isTerminal(DoneTask(id1, source, result, stats)) shouldBe true
    Task.isTerminal(FailedTask(id1, source, new RuntimeException)) shouldBe true
    Task.isTerminal(CanceledTask(id1, source)) shouldBe true
  }

  "TaskId" should "increment" in {
    id1 + 10 shouldBe TaskId(id1.value + 10)
  }

  "ScheduledTask" should "transition to state running" in {
    scheduled.run shouldBe RunningTask(scheduled.id, scheduled.source, None)
  }

  it should "transition to state canceled" in {
    scheduled.cancel shouldBe CanceledTask(scheduled.id, scheduled.source)
  }

  "RunningTask" should "accept new stats" in {
    running.stats(stats) shouldBe RunningTask(running.id, running.source, Some(stats))
  }

  it should "transition to state done" in {
    running.done(result, stats) shouldBe DoneTask(running.id, running.source, result, stats)
  }

  it should "transition to state failed" in {
    running.fail(new RuntimeException) should matchPattern {
      case FailedTask(`running`.`id`, `running`.`source`, _: RuntimeException) =>
    }
  }

  "TaskStats.overall" should "calculate speed" in {
    val stats = TaskStats.overall(linesProcessed = 1000, millis = 3000)
    stats shouldBe TaskStats(linesProcessed = 1000, linesProcessedPerSec = BigDecimal("333.33"))
  }
}
