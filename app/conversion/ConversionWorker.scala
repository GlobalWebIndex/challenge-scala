package conversion

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.OverflowStrategy
import akka.stream.alpakka.csv.scaladsl.CsvParsing
import akka.stream.alpakka.csv.scaladsl.CsvToMap
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSource
import akka.util.ByteString
import models.TaskId
import play.api.libs.json.Json

import java.nio.file.Path

trait ConversionWorker {
  def cancel(onCancel: () => Unit): Unit
  def currentCount(onCount: Long => Unit): Unit
}

trait ConversionWorkerFactory {
  def createWorker(
      taskId: TaskId,
      url: Uri,
      result: Path,
      onCount: Long => Unit,
      onFailure: () => Unit
  )(implicit as: ActorSystem[_]): ConversionWorker
}

class DefaultConversionWorkerCreator(
    source: ConversionSource,
    sink: ConversionSink
) extends ConversionWorkerFactory {
  def createWorker(
      taskId: TaskId,
      url: Uri,
      result: Path,
      onCount: Long => Unit,
      onFailure: () => Unit
  )(implicit as: ActorSystem[_]): ConversionWorker =
    new ConversionWorkerImpl(
      source.make(url),
      sink.make(result),
      onCount,
      onFailure
    )
}

object ConversionWorkerImpl {
  private sealed trait Message
  private object Message {
    object Line extends Message
    final case class Cancel(onCancel: () => Unit) extends Message
    final case class Count(onCount: Long => Unit) extends Message
    final case class Failure(onFail: () => Unit) extends Message
  }

  private final case class CounterState(
      count: Long,
      onFinish: Long => Unit
  ) {
    def handleMessage(message: Message): CounterState = message match {
      case Message.Line             => copy(count = count + 1)
      case Message.Cancel(onCancel) => copy(onFinish = _ => onCancel())
      case Message.Failure(onFail)  => copy(onFinish = _ => onFail())
      case Message.Count(onCount)   => { onCount(count); this }
    }
    def finish(): Unit = onFinish(count)
  }
}

class ConversionWorkerImpl(
    source: Source[ByteString, _],
    result: Sink[ByteString, _],
    onDone: Long => Unit,
    onFail: () => Unit
)(implicit as: ActorSystem[_])
    extends ConversionWorker {
  import ConversionWorkerImpl._

  private val items =
    source
      .via(CsvParsing.lineScanner())
      .via(CsvToMap.toMap())
      .map(Json.toJson(_))
      .map(Json.stringify(_))
      .intersperse("[", ",\n", "]")
      .map(ByteString(_))

  private val commands = ActorSource.actorRef[Message](
    PartialFunction.empty,
    PartialFunction.empty,
    0,
    OverflowStrategy.fail
  )
  private val counter =
    Flow
      .fromFunction[ByteString, Message](_ => Message.Line)
      .recover(_ => Message.Failure(onFail))
      .mergeMat(commands, true)(Keep.right)
      .takeWhile({ case Message.Cancel(_) => false; case _ => true }, true)
      .statefulMap(() => CounterState(0, onDone))(
        (c, m) => (c.handleMessage(m), ()),
        { c => c.onFinish(c.count); None }
      )
      .to(Sink.ignore)

  private val actor = items.alsoToMat(counter)(Keep.right).to(result).run()

  def cancel(onCancel: () => Unit): Unit = actor ! Message.Cancel(onCancel)
  def currentCount(onCount: Long => Unit): Unit = actor ! Message.Count(onCount)
}
