package pl.datart.csvtojson.service

import akka.http.scaladsl.model.Uri
import cats.effect.kernel.Async
import cats.syntax.flatMap._
import pl.datart.csvtojson.model._

trait TaskEnqueuer[F[_]] {
  def enqueue(rawUri: RawUri): F[TaskId]
}

class TaskEnqueuerImpl[F[_]](implicit async: Async[F]) extends TaskEnqueuer[F] {
  override def enqueue(rawUri: RawUri): F[TaskId] = {
    async
      .pure {
        Uri(rawUri.uri)
      }
      .flatMap(_ => TaskIdComp.create[F])
  }
}
