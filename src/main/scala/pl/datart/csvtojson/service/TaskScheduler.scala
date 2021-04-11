package pl.datart.csvtojson.service

import akka.http.scaladsl.model.Uri
import cats.effect._
import cats.syntax.all._
import cats.effect.syntax.all._
import pl.datart.csvtojson.model.TaskState._
import pl.datart.csvtojson.model._

import scala.concurrent._

trait TaskScheduler[F[_]] {
  def schedule(rawUri: RawUri): F[TaskId]
  def cancelTask(taskId: TaskId): F[Option[CancellationResult]]
}

@SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.ImplicitParameter"))
class TaskSchedulerImpl[F[_]](
    taskService: TaskService[F],
    taskRunner: TaskRunner[F]
)(implicit
    async: Async[F],
    schedulerExecutionContext: ExecutionContext
) extends TaskScheduler[F] {

  override def schedule(rawUri: RawUri): F[TaskId] = {
    for {
      uri    <- async.pure(Uri(rawUri.uri))
      taskId <- TaskIdComp.create[F]
      task    = Task(
                  taskId = taskId,
                  uri = uri,
                  state = TaskState.Scheduled,
                  startTime = None,
                  endTime = None
                )
      _      <- taskService.addTask(task)
      _      <- taskRunner.run(taskId, uri).startOn(schedulerExecutionContext)
    } yield taskId
  }

  override def cancelTask(taskId: TaskId): F[Option[CancellationResult]] = {
    taskService.getTask(taskId).flatMap { taskOption =>
      taskOption.traverse { task =>
        task.state match {
          case Scheduled | Running =>
            taskService
              .updateTask(taskId, TaskState.Canceled)
              .map(_ => CancellationResult.Canceled)
          case otherState          =>
            async.pure(
              CancellationResult.NotCanceled(
                s"Task ${taskId.taskId} not canceled, already in state: ${otherState.asString}"
              )
            )
        }
      }
    }
  }
}
