package pl.datart.csvtojson.service

import akka.http.scaladsl.model.Uri
import cats.effect._
import cats.effect.std.Semaphore
import fs2.concurrent.SignallingRef
import org.scalatest.funspec._
import org.scalatest.matchers.should.Matchers
import pl.datart.csvtojson.model._
import pl.datart.csvtojson.service.TaskService.StatsFlow
import pl.datart.csvtojson.util.Cancellable
import pl.datart.csvtojson.util.FAdapter.FAdapterIOFGlobal.adapter._

import java.util.Date

@SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
class TaskSchedulerImplTest extends AsyncFunSpec with Matchers {

  private implicit val async: Async[IO] = IO.asyncForIO

  private val mockedTaskRunner = new TaskRunner[IO] {
    override def run(taskId: TaskId, uri: Uri, signal: SignallingRef[IO, Boolean]): IO[Unit] =
      IO(())
  }

  describe("schedule") {
    it("schedules task to be done") {
      val mockedTaskService: TaskService[IO] = new TaskService[IO] {
        override def getTasks: IO[Iterable[TaskId]]                                 = IO(Iterable.empty[TaskId])
        override def getTask(taskId: TaskId): IO[Option[Task]]                      = IO(Option.empty[Task])
        override def updateTask(taskId: TaskId, state: TaskState): IO[Option[Task]] = IO.pure(Option.empty[Task])
        override def getStats(taskId: TaskId): IO[Option[StatsFlow]]                = IO(None)
      }

      for {
        tasks               <- Ref[IO].of(Map.empty[TaskId, Task])
        semaphore           <- Semaphore[IO](2)
        testedImplementation = new TaskSchedulerImpl(tasks, semaphore, mockedTaskService, mockedTaskRunner)
        taskId              <- testedImplementation.schedule(RawUri(""))
        savedTask           <- tasks.get.map(_.get(taskId))
      } yield savedTask should not be empty
    }
  }

  describe("cancel") {
    it("returns empty cancellation result if there is no such a task to cancel") {
      val mockedTaskService = new TaskService[IO] {
        override def getTasks: IO[Iterable[TaskId]]                                 = IO(Iterable.empty[TaskId])
        override def getTask(taskId: TaskId): IO[Option[Task]]                      = IO(Option.empty[Task])
        override def updateTask(taskId: TaskId, state: TaskState): IO[Option[Task]] = IO.pure(Option.empty[Task])
        override def getStats(taskId: TaskId): IO[Option[StatsFlow]]                = IO(None)
      }

      for {
        taskId              <- TaskIdComp.create
        tasks               <- Ref[IO].of(Map.empty[TaskId, Task])
        semaphore           <- Semaphore[IO](2)
        testedImplementation = new TaskSchedulerImpl(tasks, semaphore, mockedTaskService, mockedTaskRunner)
        cancellationResult  <- testedImplementation.cancelTask(taskId)
      } yield cancellationResult shouldBe None
    }

    it("returns nonempty cancellation success result if there is such a task in a valid state") {
      def mockedTaskService(task: Task): TaskService[IO] =
        new TaskService[IO] {
          override def getTasks: IO[Iterable[TaskId]]                                 = IO(Iterable.single(task.taskId))
          override def getTask(taskId: TaskId): IO[Option[Task]]                      = IO(Option(task))
          override def updateTask(taskId: TaskId, state: TaskState): IO[Option[Task]] = IO.pure(Option.empty[Task])
          override def getStats(taskId: TaskId): IO[Option[StatsFlow]]                = IO(None)
        }

      val mockedCancellable = Option.empty[Cancellable[Any]]

      for {
        taskId              <- TaskIdComp.create
        task                 = Task(taskId, Uri(""), TaskState.Scheduled, mockedCancellable, new Date(), None, None)
        tasks               <- Ref[IO].of(Map.empty[TaskId, Task])
        semaphore           <- Semaphore[IO](2)
        testedImplementation = new TaskSchedulerImpl(tasks, semaphore, mockedTaskService(task), mockedTaskRunner)
        cancellationResult  <- testedImplementation.cancelTask(taskId)
      } yield cancellationResult shouldBe Option[CancellationResult](CancellationResult.Canceled)
    }

    it("returns nonempty cancellation failure result if there is such a task in a terminal state") {
      def mockedTaskService(task: Task): TaskService[IO] =
        new TaskService[IO] {
          override def getTasks: IO[Iterable[TaskId]]                                 = IO(Iterable.single(task.taskId))
          override def getTask(taskId: TaskId): IO[Option[Task]]                      = IO(Option(task))
          override def updateTask(taskId: TaskId, state: TaskState): IO[Option[Task]] = IO.pure(Option.empty[Task])
          override def getStats(taskId: TaskId): IO[Option[StatsFlow]]                = IO(Option.empty[StatsFlow])
        }

      def expectedError(task: Task): String =
        s"Task ${task.taskId.taskId} not canceled, already in state: ${task.state.asString}"

      for {
        taskId              <- TaskIdComp.create
        task                 = Task(taskId, Uri(""), TaskState.Done, None, new Date(), None, None)
        tasks               <- Ref[IO].of(Map.empty[TaskId, Task])
        semaphore           <- Semaphore[IO](2)
        testedImplementation = new TaskSchedulerImpl(tasks, semaphore, mockedTaskService(task), mockedTaskRunner)
        cancellationResult  <- testedImplementation.cancelTask(taskId)
      } yield cancellationResult shouldBe Option[CancellationResult](
        CancellationResult.NotCanceled(expectedError(task))
      )
    }
  }
}
