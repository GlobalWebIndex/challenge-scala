package pl.datart.csvtojson.util

import cats.effect.IO
import cats.effect.unsafe.IORuntime

import scala.concurrent.Future

@SuppressWarnings(Array("org.wartremover.warts.ImplicitConversion"))
trait FConverter[F[_], G[_]] {
  implicit def toFuture[T](a: F[T]): G[T]
}

@SuppressWarnings(Array("org.wartremover.warts.ImplicitConversion", "org.wartremover.warts.ImplicitParameter"))
object FConverter {
  implicit class FConvert[F[_], G[_], T](a: F[T])(implicit fConverter: FConverter[F, G]) {
    implicit def toFuture: G[T] = fConverter.toFuture(a)
  }

  class FConverterIOF(implicit ioRuntime: IORuntime) extends FConverter[IO, Future] {
    implicit def toFuture[T](a: IO[T]): Future[T] = a.unsafeToFuture()
  }
}
