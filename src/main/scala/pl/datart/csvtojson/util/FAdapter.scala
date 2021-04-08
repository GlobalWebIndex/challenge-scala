package pl.datart.csvtojson.util

import cats.effect.IO
import cats.effect.unsafe.IORuntime

import scala.concurrent.Future

@SuppressWarnings(Array("org.wartremover.warts.ImplicitConversion"))
trait FAdapter[F[_], G[_]] {
  implicit def adapt[T](a: F[T]): G[T]
}

@SuppressWarnings(Array("org.wartremover.warts.ImplicitConversion", "org.wartremover.warts.ImplicitParameter"))
object FAdapter {
  implicit class FAdapt[F[_], G[_], T](a: F[T])(implicit fAdapter: FAdapter[F, G]) {
    implicit def adapt: G[T] = fAdapter.adapt(a)
  }

  class FAdapterIOF(implicit ioRuntime: IORuntime) extends FAdapter[IO, Future] {
    implicit def adapt[T](a: IO[T]): Future[T] = a.unsafeToFuture()
  }

  object FAdapterIOFGlobal {
    val adapter: FAdapter[IO, Future] = new FAdapterIOF()(IORuntime.global)
  }
}
