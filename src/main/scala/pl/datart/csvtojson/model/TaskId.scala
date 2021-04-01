package pl.datart.csvtojson.model

import cats.effect.Async

import java.util.UUID

final case class TaskId(taskId: String)

object TaskIdComp {
  def create[F[_]](implicit async: Async[F]): F[TaskId] =
    async.pure {
      TaskId(UUID.randomUUID().toString)
    }
}
