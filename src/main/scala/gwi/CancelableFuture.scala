package gwi

import java.util.concurrent.{Callable, FutureTask}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

class Cancellable[T](executionContext: ExecutionContext, mothodBody: () => T) {
  private val promise = Promise[T]()

  def future:Future[T] = promise.future

  private val jf: FutureTask[T] = new FutureTask[T](
    new Callable[T] {
      override def call(): T = mothodBody()
    }
  ) {
    override def done(): Unit = promise.complete(Try(get()))
  }

  def isCanceled(): Boolean = jf.isCancelled

  def cancel(): Unit = jf.cancel(true)

  executionContext.execute(jf)
}