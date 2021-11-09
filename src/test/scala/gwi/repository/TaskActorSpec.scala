package gwi.repository

import akka.actor.{ActorSystem, typed}
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.testkit.TestKit
import akka.util.Timeout
import akka.actor.typed.scaladsl.AskPattern._
import com.gwi.repository.TaskActor
import com.gwi.repository.TaskActor.{Get, GetTaskIds, SetLinesProcessed, Upsert}
import gwi.SampleData
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContextExecutor

class TaskActorSpec
    extends TestKit(ActorSystem("TaskActorSpec"))
    with AsyncFlatSpecLike
    with should.Matchers
    with AsyncMockFactory
    with SampleData {

  implicit val ec: ExecutionContextExecutor = system.dispatcher
  implicit val timeout: Timeout = 3.seconds
  implicit val typedSystem: typed.ActorSystem[Nothing] = system.toTyped

  val lines = 3000L

  "TaskActor" should "handle properly the workflow" in {
    val taskActor: ActorRef[TaskActor.TaskCommand] = system.spawn(TaskActor.apply(), "TaskActor")
    for {
      emptyTask <- taskActor.ask(ref => Get(sampleTask.id, ref))
      emptyTaskIds <- taskActor.ask(ref => GetTaskIds(ref))
      createdTaskId <- taskActor.ask(ref => Upsert(sampleTask, ref))
      taskDetail <- taskActor.ask(ref => Get(sampleTask.id, ref))
      updatedLines <- taskActor.ask(ref => SetLinesProcessed(sampleTask.id, lines, ref))
      updatedTaskDetails <- taskActor.ask(ref => Get(sampleTask.id, ref))
      taskIds <- taskActor.ask(ref => GetTaskIds(ref))
    } yield {
      emptyTask shouldBe None
      emptyTaskIds shouldBe Set.empty
      createdTaskId shouldBe sampleTask.id
      taskDetail shouldEqual Some(sampleTask)
      updatedLines shouldBe lines
      updatedTaskDetails shouldBe Some(sampleTask.copy(linesProcessed = lines))
      taskIds shouldEqual Set(sampleTask.id)
    }
  }
}
