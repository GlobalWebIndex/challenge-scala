package pl.datart.csvtojson.service

import cats.effect._
import cats.syntax.functor._
import pl.datart.csvtojson.model._

trait TaskService[F[_]] {
  def getTasks: F[Iterable[TaskId]]
  def getTask(taskId: TaskId): F[Option[Task]]
}

class TaskServiceImpl[F[_]](tasks: Ref[F, Map[TaskId, Task]])(implicit async: Async[F]) extends TaskService[F] {
  override def getTasks: F[Iterable[TaskId]] = {
    tasks.get.map(_.keys)
  }

  override def getTask(taskId: TaskId): F[Option[Task]] = {
    tasks.get.map(_.get(taskId))
  }
}
