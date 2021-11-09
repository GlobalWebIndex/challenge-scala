package gwi.api

import akka.http.scaladsl.model.Uri
import com.gwi.Main.ServerUri
import com.gwi.api.{TaskDetail, TaskState, TaskTransformations}
import com.gwi.execution.Task
import gwi.SampleData
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.time.Instant
import java.util.UUID

class TaskTransformationsSpec extends AnyFlatSpec with should.Matchers with SampleData {

  val epochSecs = 1636154312

  "TaskDetail.getLinesRate" should "correctly calculate processed lines/s rate" in {
    TaskTransformations.getLinesRate(
      sampleTask.copy(startedAt = Some(Instant.ofEpochSecond(epochSecs)), endedAt = Some(Instant.ofEpochSecond(epochSecs + 2)))
    ) shouldBe 500
  }

  it should "properly handle division by 0" in {
    TaskTransformations.getLinesRate(
      sampleTask.copy(startedAt = Some(Instant.ofEpochSecond(epochSecs)), endedAt = Some(Instant.ofEpochSecond(epochSecs)))
    ) shouldBe -1
  }

  "TaskDetail.getMaybeResultUri" should "return some uri for Done task" in {
    TaskTransformations.getMaybeResultUri(sampleTask.copy(state = TaskState.Done)) shouldBe Some(s"$ServerUri/task/${sampleTask.id}/result")
  }

  it should "return empty for other states" in {
    TaskTransformations.getMaybeResultUri(sampleTask.copy(state = TaskState.Canceled)) shouldBe None
    TaskTransformations.getMaybeResultUri(sampleTask.copy(state = TaskState.Failed)) shouldBe None
    TaskTransformations.getMaybeResultUri(sampleTask.copy(state = TaskState.Running)) shouldBe None
    TaskTransformations.getMaybeResultUri(sampleTask.copy(state = TaskState.Scheduled)) shouldBe None
  }

  "TaskDetail.fromTask" should "properly task to taskDetail" in {
    val task = sampleTask.copy(
      state = TaskState.Done,
      startedAt = Some(Instant.ofEpochSecond(epochSecs)),
      endedAt = Some(Instant.ofEpochSecond(epochSecs + 1))
    )
    val expectedTaskDetail =
      TaskDetail(sampleTask.id, sampleTask.linesProcessed, 1000, sampleTask.state, Some(s"$ServerUri/task/${sampleTask.id}/result"))
    TaskTransformations.toTaskDetail(task) shouldEqual expectedTaskDetail
  }
}
