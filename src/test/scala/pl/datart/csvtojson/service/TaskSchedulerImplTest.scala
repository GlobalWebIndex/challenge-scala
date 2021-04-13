package pl.datart.csvtojson.service

import akka.http.scaladsl.model.Uri
import cats.syntax.all._
import cats.effect._
import org.scalatest.funspec._
import org.scalatest.matchers.should.Matchers
import pl.datart.csvtojson.model._
import pl.datart.csvtojson.service.TaskService.StatsSource
import pl.datart.csvtojson.util.FAdapter.FAdapterIOFGlobal.adapter._

@SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
class TaskSchedulerImplTest extends AsyncFunSpec with Matchers {

  private implicit val async: Async[IO] = IO.asyncForIO

  private val mockedTaskRunner = new TaskRunner[IO] {
    override def run(taskId: TaskId, uri: Uri): IO[Unit] =
      IO(())
  }

  describe("schedule") {
    it("schedules task to be done") {
      val mockedTaskService: TaskService[IO] = new TaskService[IO] {
        override def addTask(task: Task): IO[Unit]                                  = IO.unit
        override def getTasks: IO[Iterable[TaskId]]                                 = IO(Iterable.empty[TaskId])
        override def getTask(taskId: TaskId): IO[Option[Task]]                      = IO(Option.empty[Task])
        override def updateTask(taskId: TaskId, state: TaskState): IO[Option[Task]] = IO.pure(Option.empty[Task])
        override def getStats(taskId: TaskId): IO[Option[StatsSource]]              = IO(None)
      }

      for {
        testedImplementation <- new TaskSchedulerImpl(mockedTaskService, mockedTaskRunner).pure
        taskId               <- testedImplementation.schedule(RawUri(""))
      } yield taskId.taskId should not be empty
    }
  }

  describe("cancel") {
    it("returns empty cancellation result if there is no such a task to cancel") {
      val mockedTaskService = new TaskService[IO] {
        override def addTask(task: Task): IO[Unit]                                  = IO.unit
        override def getTasks: IO[Iterable[TaskId]]                                 = IO(Iterable.empty[TaskId])
        override def getTask(taskId: TaskId): IO[Option[Task]]                      = IO(Option.empty[Task])
        override def updateTask(taskId: TaskId, state: TaskState): IO[Option[Task]] = IO.pure(Option.empty[Task])
        override def getStats(taskId: TaskId): IO[Option[StatsSource]]              = IO(None)
      }

      for {
        taskId               <- TaskIdComp.create
        testedImplementation <- new TaskSchedulerImpl(mockedTaskService, mockedTaskRunner).pure
        cancellationResult   <- testedImplementation.cancelTask(taskId)
      } yield cancellationResult shouldBe None
    }

    it("returns nonempty cancellation success result if there is such a task in a valid state") {
      def mockedTaskService(task: Task): TaskService[IO] =
        new TaskService[IO] {
          override def addTask(task: Task): IO[Unit]                                  = IO.unit
          override def getTasks: IO[Iterable[TaskId]]                                 = IO(Iterable.single(task.taskId))
          override def getTask(taskId: TaskId): IO[Option[Task]]                      = IO(Option(task))
          override def updateTask(taskId: TaskId, state: TaskState): IO[Option[Task]] = IO.pure(Option.empty[Task])
          override def getStats(taskId: TaskId): IO[Option[StatsSource]]              = IO(None)
        }
      for {
        taskId <- TaskIdComp.create
        task                  = Task(taskId, Uri(""), TaskState.Scheduled, None, None)
        testedImplementation <- new TaskSchedulerImpl(mockedTaskService(task), mockedTaskRunner).pure
        cancellationResult   <- testedImplementation.cancelTask(taskId)
      } yield cancellationResult shouldBe Option[CancellationResult](CancellationResult.Canceled)
    }

    it("returns nonempty cancellation failure result if there is such a task in a terminal state") {
      def mockedTaskService(task: Task): TaskService[IO] =
        new TaskService[IO] {
          override def addTask(task: Task): IO[Unit]                                  = IO.unit
          override def getTasks: IO[Iterable[TaskId]]                                 = IO(Iterable.single(task.taskId))
          override def getTask(taskId: TaskId): IO[Option[Task]]                      = IO(Option(task))
          override def updateTask(taskId: TaskId, state: TaskState): IO[Option[Task]] = IO.pure(Option.empty[Task])
          override def getStats(taskId: TaskId): IO[Option[StatsSource]]              = IO(Option.empty[StatsSource])
        }

      def expectedError(task: Task): String =
        s"Task ${task.taskId.taskId} not canceled, already in state: ${task.state.asString}"

      for {
        taskId               <- TaskIdComp.create
        task                  = Task(taskId, Uri(""), TaskState.Done, None, None)
        testedImplementation <- new TaskSchedulerImpl(mockedTaskService(task), mockedTaskRunner).pure
        cancellationResult   <- testedImplementation.cancelTask(taskId)
      } yield cancellationResult shouldBe Option[CancellationResult](
        CancellationResult.NotCanceled(expectedError(task))
      )
    }
  }
}
