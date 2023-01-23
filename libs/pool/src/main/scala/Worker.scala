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

/** A running task */
trait Worker {

  /** Request to cancel the task
    *
    * @param onCancel
    *   Callback to acknowledge the finished task and return the number of
    *   processed items
    */
  def cancel(onCancel: Long => Unit): Unit

  /** Request to provide the number of processed items
    *
    * @param onCount
    *   Callback to return the number of processed items
    */
  def currentCount(onCount: Long => Unit): Unit
}

/** A factory, creating new workers */
trait WorkerFactory[ID, IN, OUT] {

  /** Create a new worker
    *
    * @param taskId
    *   Task identifier
    * @param url
    *   Source address
    * @param result
    *   Destination address
    * @param onDone
    *   Callback to report the task finishing normally
    * @param onFailure
    *   Callback to report the task failing
    * @return
    *   The newly created worker
    */
  def createWorker(
      taskId: ID,
      url: IN,
      result: OUT,
      onDone: Long => Unit,
      onFailure: Long => Unit
  )(implicit as: ActorSystem[_]): Worker
}

/** Factory for [[WorkerFactory]] */
object WorkerFactory {

  /** Create a new [[WorkerFactory]]
    *
    * @param fetch
    *   Required utilities to fetch the data from source
    * @param saver
    *   Required utilities to save the data
    * @return
    *   The newly created [[WorkerFactory]]
    */
  def apply[ID, IN, OUT, ITEM](
      fetch: Fetch[IN, ITEM],
      saver: Saver[ID, OUT, ITEM]
  ): WorkerFactory[ID, IN, OUT] = new WorkerFactory[ID, IN, OUT] {
    def createWorker(
        taskId: ID,
        url: IN,
        result: OUT,
        onDone: Long => Unit,
        onFailure: Long => Unit
    )(implicit as: ActorSystem[_]): Worker =
      new WorkerImpl(
        fetch.make(url),
        saver.make(result),
        onDone,
        onFailure
      )
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
      result: Sink[ITEM, _],
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

    private val actor = source.alsoTo(result).runWith(counter)

    def cancel(onCancel: Long => Unit): Unit = actor ! Message.Cancel(onCancel)
    def currentCount(onCount: Long => Unit): Unit =
      actor ! Message.Count(onCount)
  }
}
