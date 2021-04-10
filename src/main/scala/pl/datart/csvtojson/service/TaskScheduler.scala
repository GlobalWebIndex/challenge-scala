package pl.datart.csvtojson.service

import akka.http.scaladsl.model.Uri
import cats.effect.Ref
import cats.effect._
import cats.effect.std._
import cats.syntax.all._
import cats.effect.syntax.all._
import fs2.concurrent.SignallingRef
import pl.datart.csvtojson.model.TaskState._
import pl.datart.csvtojson.model._
import pl.datart.csvtojson.util.Cancellable

import java.util.Date
import scala.concurrent._

trait TaskScheduler[F[_]] {
  def schedule(rawUri: RawUri): F[TaskId]
  def cancelTask(taskId: TaskId): F[Option[CancellationResult]]
}

@SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.ImplicitParameter"))
class TaskSchedulerImpl[F[_]](
    tasks: Ref[F, Map[TaskId, Task]],
    semaphore: Semaphore[F],
    taskService: TaskService[F],
    taskRunner: TaskRunner[F]
)(implicit
    async: Async[F],
    schedulerExecutionContext: ExecutionContext
) extends TaskScheduler[F] {

  override def schedule(rawUri: RawUri): F[TaskId] = {
    for {
      signal <- SignallingRef[F, Boolean](false)
      uri    <- async.pure(Uri(rawUri.uri))
      taskId <- TaskIdComp.create[F]
      _      <- taskRunner.run(taskId, uri, signal).startOn(schedulerExecutionContext)
      task    = Task(
                  taskId = taskId,
                  uri = uri,
                  state = TaskState.Scheduled,
                  cancelable = Option(new Cancellable[Any] {
                    def cancel: F[Unit] = {
                      signal.set(true)
                    }
                  }),
                  scheduleTime = new Date(),
                  startTime = None,
                  endTime = None
                )
      _      <- tasks.update(oldTasks => oldTasks + (taskId -> task))
    } yield taskId
  }

  override def cancelTask(taskId: TaskId): F[Option[CancellationResult]] = {
    taskService.getTask(taskId).flatMap { taskOption =>
      taskOption.fold(async.pure(Option.empty[CancellationResult])) { task =>
        task.state match {
          case Scheduled  =>
            cancel(task.cancelable)
              .flatMap(_ => taskService.updateTask(taskId, TaskState.Canceled))
              .map(_ => Option[CancellationResult](CancellationResult.Canceled))
          case Running    =>
            cancel(task.cancelable)
              .flatMap(_ => taskService.updateTask(taskId, TaskState.Canceled))
              .flatMap(_ => semaphore.release)
              .map(_ => Option[CancellationResult](CancellationResult.Canceled))
          case otherState =>
            async.pure {
              Option(
                CancellationResult.NotCanceled(
                  s"Task ${taskId.taskId} not canceled, already in state: ${otherState.asString}"
                )
              )
            }
        }
      }
    }
  }

  private def cancel(cancelable: Option[pl.datart.csvtojson.util.Cancellable[Any]]): F[Unit] = {
    cancelable.fold(async.unit)(cancelable => async.pure(cancelable.cancel).map(_ => (())))
  }
}
