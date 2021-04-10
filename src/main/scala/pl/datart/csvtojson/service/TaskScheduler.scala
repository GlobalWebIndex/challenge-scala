package pl.datart.csvtojson.service

import akka.http.scaladsl.model.Uri
import cats.effect.Ref
import cats.effect.kernel.Async
import cats.syntax.flatMap._
import cats.syntax.functor._
import pl.datart.csvtojson.model.TaskState._
import pl.datart.csvtojson.model._
import pl.datart.csvtojson.util.Cancellable

import java.util.Date

trait TaskScheduler[F[_]] {
  def schedule(rawUri: RawUri): F[TaskId]
  def cancelTask(taskId: TaskId): F[Option[CancellationResult]]
}

@SuppressWarnings(Array("org.wartremover.warts.Any"))
class TaskSchedulerImpl[F[_]](tasks: Ref[F, Map[TaskId, Task]], taskService: TaskService[F])(implicit async: Async[F])
    extends TaskScheduler[F] {
  override def schedule(rawUri: RawUri): F[TaskId] = {
    for {
      uri    <- async.pure(Uri(rawUri.uri))
      taskId <- TaskIdComp.create[F]
      task    = Task(
                  taskId = taskId,
                  uri = uri,
                  state = TaskState.Scheduled,
                  cancelable = None,
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
          case Scheduled | Running =>
            cancel(task.cancelable)
              .flatMap(_ =>
                tasks.update(oldTasks =>
                  oldTasks.removed(taskId) + (taskId -> task.copy(state = TaskState.Canceled, cancelable = None))
                )
              )
              .map(_ => Option[CancellationResult](CancellationResult.Canceled))
          case otherState          =>
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

  private def cancel(cancelable: Option[Cancellable[Any]]): F[Unit] = {
    cancelable.fold(async.unit)(cancelable => async.pure(cancelable.cancel).map(_ => (())))
  }
}
