package pool

import pool.dependencies.Fetch
import pool.dependencies.Saver

import akka.actor.typed.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSource

trait Worker {
  def cancel(onCancel: Long => Unit): Unit
  def currentCount(onCount: Long => Unit): Unit
}

trait WorkerFactory[ID, IN, OUT] {
  def createWorker(
      taskId: ID,
      url: IN,
      result: OUT,
      onCount: Long => Unit,
      onFailure: Long => Unit
  )(implicit as: ActorSystem[_]): Worker
}

class DefaultWorkerFactory[ID, IN, OUT, ITEM](
    fetch: Fetch[IN, ITEM],
    saver: Saver[ID, OUT, ITEM]
) extends WorkerFactory[ID, IN, OUT] {
  def createWorker(
      taskId: ID,
      url: IN,
      result: OUT,
      onCount: Long => Unit,
      onFailure: Long => Unit
  )(implicit as: ActorSystem[_]): Worker =
    new WorkerImpl(
      fetch.make(url),
      saver.make(result),
      onCount,
      onFailure
    )
}

object WorkerImpl {
  private sealed trait Message
  private object Message {
    object Line extends Message
    final case class Cancel(onCancel: Long => Unit) extends Message
    final case class Count(onCount: Long => Unit) extends Message
    final case class Failure(onFail: Long => Unit) extends Message
  }

  private final case class CounterState(
      count: Long,
      onFinish: Long => Unit
  ) {
    def handleMessage(message: Message): CounterState = message match {
      case Message.Line             => copy(count = count + 1)
      case Message.Cancel(onCancel) => copy(onFinish = onCancel)
      case Message.Failure(onFail)  => copy(onFinish = onFail)
      case Message.Count(onCount)   => { onCount(count); this }
    }
    def finish(): Unit = onFinish(count)
  }
}

class WorkerImpl[ITEM](
    source: Source[ITEM, _],
    result: Sink[ITEM, _],
    onDone: Long => Unit,
    onFail: Long => Unit
)(implicit as: ActorSystem[_])
    extends Worker {
  import WorkerImpl._

  private val commands = ActorSource.actorRef[Message](
    PartialFunction.empty,
    PartialFunction.empty,
    0,
    OverflowStrategy.fail
  )
  private val counter =
    Flow
      .fromFunction[ITEM, Message](_ => Message.Line)
      .recover(_ => Message.Failure(onFail))
      .mergeMat(commands, true)(Keep.right)
      .takeWhile({ case Message.Cancel(_) => false; case _ => true }, true)
      .statefulMap(() => CounterState(0, onDone))(
        (c, m) => (c.handleMessage(m), ()),
        { c => c.onFinish(c.count); None }
      )
      .to(Sink.ignore)

  private val actor = source.alsoToMat(counter)(Keep.right).to(result).run()

  def cancel(onCancel: Long => Unit): Unit = actor ! Message.Cancel(onCancel)
  def currentCount(onCount: Long => Unit): Unit = actor ! Message.Count(onCount)
}
