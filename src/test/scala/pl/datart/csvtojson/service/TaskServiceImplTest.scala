package pl.datart.csvtojson.service

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl._
import cats.effect._
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import pl.datart.csvtojson.model._
import pl.datart.csvtojson.service.TaskService.StatsSource
import pl.datart.csvtojson.util.FAdapter
import pl.datart.csvtojson.util.FAdapter.FAdapterIOFGlobal.adapter
import pl.datart.csvtojson.util.FAdapter.FAdapterIOFGlobal.adapter._

import scala.concurrent.Future

@SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
class TaskServiceImplTest extends AsyncFunSpec with Matchers {

  private implicit val async: Async[IO]               = IO.asyncForIO
  private implicit val actorSystem: ActorSystem       = ActorSystem("test-task-service-as")
  private implicit val fAdapter: FAdapter[IO, Future] = adapter

  describe("addTask") {
    it("adds new task") {
      for {
        tasks               <- Ref[IO].of(Map.empty[TaskId, Task])
        testedImplementation = new TaskServiceImpl(tasks, StatsComposerImpl)
        taskId              <- TaskIdComp.create
        task                 = Task(taskId, Uri(""), TaskState.Scheduled, None, None)
        _                   <- testedImplementation.addTask(task)
        fetchedTasks        <- testedImplementation.getTasks
      } yield fetchedTasks shouldBe Set(taskId)
    }
  }

  describe("getTasks") {
    it("returns empty collection of tasks if there aren't any of them") {
      for {
        tasks               <- Ref[IO].of(Map.empty[TaskId, Task])
        testedImplementation = new TaskServiceImpl(tasks, StatsComposerImpl)
        fetchedTasks        <- testedImplementation.getTasks
      } yield fetchedTasks shouldBe empty
    }

    it("returns nonempty collection of tasks if there some of them") {
      for {
        taskId              <- TaskIdComp.create
        task                 = Task(taskId, Uri(""), TaskState.Scheduled, None, None)
        tasks               <- Ref[IO].of(Map[TaskId, Task](taskId -> task))
        testedImplementation = new TaskServiceImpl(tasks, StatsComposerImpl)
        fetchedTasks        <- testedImplementation.getTasks
      } yield fetchedTasks.size shouldBe 1
    }
  }

  describe("getTask") {
    it("returns empty result if there is no such a task") {
      for {
        taskId              <- TaskIdComp.create
        tasks               <- Ref[IO].of(Map.empty[TaskId, Task])
        testedImplementation = new TaskServiceImpl(tasks, StatsComposerImpl)
        fetchedTask         <- testedImplementation.getTask(taskId)
      } yield `fetchedTask` shouldBe None
    }

    it("returns nonempty result of matching task for a given task id") {
      for {
        taskId              <- TaskIdComp.create
        task                 = Task(taskId, Uri(""), TaskState.Scheduled, None, None)
        tasks               <- Ref[IO].of(Map[TaskId, Task](taskId -> task))
        testedImplementation = new TaskServiceImpl(tasks, StatsComposerImpl)
        fetchedTask         <- testedImplementation.getTask(taskId)
      } yield fetchedTask shouldBe Option(task)
    }
  }

  describe("updateTask") {
    it("should update existing task correctly") {
      for {
        taskId              <- TaskIdComp.create
        task                 = Task(
                                 taskId = taskId,
                                 uri = Uri(""),
                                 state = TaskState.Scheduled,
                                 startTime = None,
                                 endTime = None
                               )
        tasks               <- Ref[IO].of(Map(taskId -> task))
        testedImplementation = new TaskServiceImpl(tasks, StatsComposerImpl)
        _                   <- testedImplementation.updateTask(taskId, TaskState.Running)
        expectedRunning     <- tasks.get.map(_.get(taskId).map(_.state))
        _                   <- testedImplementation.updateTask(taskId, TaskState.Canceled)
        expectedCanceled    <- tasks.get.map(_.get(taskId).map(_.state))
        _                   <- testedImplementation.updateTask(taskId, TaskState.Failed)
        expectedFailed      <- tasks.get.map(_.get(taskId).map(_.state))
        _                   <- testedImplementation.updateTask(taskId, TaskState.Done)
        expectedDone        <- tasks.get.map(_.get(taskId).map(_.state))
      } yield List[Option[TaskState]](
        expectedRunning,
        expectedCanceled,
        expectedFailed,
        expectedDone
      ) should contain theSameElementsInOrderAs List[Option[TaskState]](
        Option(TaskState.Running),
        Option(TaskState.Canceled),
        Option(TaskState.Failed),
        Option(TaskState.Done)
      )
    }

    it("should do nothing for not existing task") {
      for {
        taskId              <- TaskIdComp.create
        tasks               <- Ref[IO].of(Map.empty[TaskId, Task])
        testedImplementation = new TaskServiceImpl(tasks, StatsComposerImpl)
        expectedEmpty       <- testedImplementation.updateTask(taskId, TaskState.Canceled)
      } yield expectedEmpty shouldBe Option.empty[Task]
    }
  }

  describe("getStats") {
    it("should return stats for existing task correctly") {
      for {
        taskId              <- TaskIdComp.create
        task                 = Task(
                                 taskId = taskId,
                                 uri = Uri(""),
                                 state = TaskState.Scheduled,
                                 startTime = None,
                                 endTime = None
                               )
        tasks               <- Ref[IO].of(Map(taskId -> task))
        testedImplementation = new TaskServiceImpl(tasks, StatsComposerImpl)
        tasksStats          <- testedImplementation.getStats(taskId)
        _                   <- testedImplementation.updateTask(taskId, TaskState.Canceled)
        message             <- tasksStats.fold[IO[Message]](IO(fail())) { statsFlow =>
                                 IO.fromFuture(IO(statsFlow.runWith(Sink.head)))
                               }
      } yield message.asTextMessage.getStrictText shouldBe
        s"""{
           |  "avgLinesPerSec": 0,
           |  "linesProcessed": 0,
           |  "state": "CANCELED"
           |}""".stripMargin
    }

    it("should not return stats for not existing task") {
      for {
        taskId              <- TaskIdComp.create
        tasks               <- Ref[IO].of(Map.empty[TaskId, Task])
        testedImplementation = new TaskServiceImpl(tasks, StatsComposerImpl)
        expectedEmpty       <- testedImplementation.getStats(taskId)
      } yield expectedEmpty shouldBe Option.empty[StatsSource]
    }
  }
}
