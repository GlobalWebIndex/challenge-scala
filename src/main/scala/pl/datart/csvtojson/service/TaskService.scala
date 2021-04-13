package pl.datart.csvtojson.service

import akka._
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl._
import cats.effect._
import cats.syntax.all._
import pl.datart.csvtojson.model.JsonFormats._
import pl.datart.csvtojson.model._
import pl.datart.csvtojson.service.TaskService.StatsSource
import spray.json._

import java.util.Date
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import pl.datart.csvtojson.util.FAdapter
import pl.datart.csvtojson.util.FAdapter._

trait TaskService[F[_]] {
  def addTask(task: Task): F[Unit]
  def getTasks: F[Iterable[TaskId]]
  def getTask(taskId: TaskId): F[Option[Task]]
  def updateTask(taskId: TaskId, state: TaskState): F[Option[Task]]
  def getStats(taskId: TaskId): F[Option[StatsSource]]
}

object TaskService {
  type StatsSource = Source[Message, _]
}

@SuppressWarnings(
  Array("org.wartremover.warts.ImplicitParameter", "org.wartremover.warts.Nothing", "org.wartremover.warts.Any")
)
class TaskServiceImpl[F[_]](tasks: Ref[F, Map[TaskId, Task]], statsComposer: StatsComposer)(implicit
    async: Async[F],
    fAdapter: FAdapter[F, Future]
) extends TaskService[F] {
  override def addTask(task: Task): F[Unit] = {
    tasks.update(_ + (task.taskId -> task))
  }

  override def getTasks: F[Iterable[TaskId]] = {
    tasks.get.map(_.keys)
  }

  override def getTask(taskId: TaskId): F[Option[Task]] = {
    tasks.get.map(_.get(taskId))
  }

  override def updateTask(taskId: TaskId, state: TaskState): F[Option[Task]] = {
    getTask(taskId).flatMap { taskOption =>
      taskOption.fold(async.pure(Option.empty[Task])) { task =>
        val newTask = state match {
          case TaskState.Running  =>
            task.copy(state = TaskState.Running, startTime = Option(new Date()))
          case TaskState.Canceled =>
            task.copy(state = TaskState.Canceled)
          case TaskState.Done     =>
            task.copy(state = TaskState.Done, endTime = Option(new Date()))
          case TaskState.Failed   =>
            task.copy(state = TaskState.Failed)
          case _                  => task
        }

        tasks.update(oldTasks => oldTasks.removed(taskId) + (taskId -> newTask)).map(_ => Option(newTask))
      }
    }
  }

  override def getStats(taskId: TaskId): F[Option[StatsSource]] = {
    getTask(taskId).flatMap { taskOption =>
      taskOption.traverse { _ =>
        val source = Source
          .tick(2.seconds, 2.seconds, NotUsed)
          .mapAsync(parallelism = 1)(_ => getTask(taskId).adapt)
          .collect {
            case Some(t) => t
          }
          .takeWhile(!_.isInTerminal, inclusive = true)
          .map(currentStats)
          .map(TextMessage(_))

        async.pure(source)
      }
    }
  }

  private def currentStats(task: Task): String = {
    statsComposer
      .createReport(task)
      .toJson(taskStatsFormat)
      .prettyPrint
  }
}
