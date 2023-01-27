package pool.akka

import pool.Worker
import pool.WorkerFactory
import pool.dependencies.Destination

import akka.actor.typed.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSource

object AkkaWorkerFactory {

  /** Create a new [[WorkerFactory]]
    *
    * @param saver
    *   Required utilities to save the data
    * @return
    *   The newly created [[WorkerFactory]]
    */
  def apply[ITEM, OUT <: Destination[Sink[ITEM, _]]](implicit
      as: ActorSystem[_]
  ): WorkerFactory[Source[ITEM, _], OUT] =
    new WorkerFactory[Source[ITEM, _], OUT] {
      def createWorker(
          source: Source[ITEM, _],
          destination: OUT,
          onDone: Long => Unit,
          onFailure: Long => Unit
      ): Worker = new WorkerImpl(source, destination.sink(), onDone, onFailure)
    }

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

  private class WorkerImpl[ITEM](
      source: Source[ITEM, _],
      destination: Sink[ITEM, _],
      onDone: Long => Unit,
      onFail: Long => Unit
  )(implicit as: ActorSystem[_])
      extends Worker {

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

    private val actor = source.alsoTo(destination).runWith(counter)

    def cancel(onCancel: Long => Unit): Unit = actor ! Message.Cancel(onCancel)
    def currentCount(onCount: Long => Unit): Unit =
      actor ! Message.Count(onCount)
  }

}
