package pl.datart.csvtojson.service

import akka.http.scaladsl.model.Uri
import cats.effect._
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import pl.datart.csvtojson.model._
import pl.datart.csvtojson.util.FAdapter.FAdapterIOFGlobal.adapter._

import java.util.Date

@SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
class TaskServiceImplTest extends AsyncFunSpec with Matchers {

  private implicit val async: Async[IO] = IO.asyncForIO

  describe("getTasks") {
    it("returns empty collection of tasks if there aren't any of them") {
      for {
        tasks               <- Ref[IO].of(Map.empty[TaskId, Task])
        testedImplementation = new TaskServiceImpl(tasks)
        fetchedTasks        <- testedImplementation.getTasks
      } yield fetchedTasks shouldBe empty
    }

    it("returns nonempty collection of tasks if there some of them") {
      for {
        taskId              <- TaskIdComp.create
        task                 = Task(taskId, Uri(""), TaskState.Scheduled, None, new Date(), None, None)
        tasks               <- Ref[IO].of(Map[TaskId, Task](taskId -> task))
        testedImplementation = new TaskServiceImpl(tasks)
        fetchedTasks        <- testedImplementation.getTasks
      } yield fetchedTasks.size shouldBe 1
    }
  }

  describe("getTask") {
    it("returns empty result if there is no such a task") {
      for {
        taskId              <- TaskIdComp.create
        tasks               <- Ref[IO].of(Map.empty[TaskId, Task])
        testedImplementation = new TaskServiceImpl(tasks)
        fetchedTask         <- testedImplementation.getTask(taskId)
      } yield `fetchedTask` shouldBe None
    }

    it("returns nonempty result of matching task for a given task id") {
      for {
        taskId              <- TaskIdComp.create
        task                 = Task(taskId, Uri(""), TaskState.Scheduled, None, new Date(), None, None)
        tasks               <- Ref[IO].of(Map[TaskId, Task](taskId -> task))
        testedImplementation = new TaskServiceImpl(tasks)
        fetchedTask         <- testedImplementation.getTask(taskId)
      } yield fetchedTask shouldBe Option(task)
    }
  }
}
