package conversion

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaTypes
import akka.stream.OverflowStrategy
import akka.stream.alpakka.csv.scaladsl.CsvParsing
import akka.stream.alpakka.csv.scaladsl.CsvToMap
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSource
import akka.util.ByteString
import play.api.libs.json.Json

import java.nio.file.Path

private sealed trait Message
private object Message {
  object Complete extends Message
  object Line extends Message
  final case class Cancel(onCancel: () => Unit) extends Message
  final case class Count(onCount: Long => Unit) extends Message
  final case class Failure(onFail: () => Unit) extends Message
}

private final case class CounterState(
    count: Long,
    onFinish: Long => Unit
) {
  def handleMessage(
      message: Message
  ): CounterState = message match {
    case Message.Complete         => this // shouldn't happen
    case Message.Line             => copy(count = count + 1)
    case Message.Cancel(onCancel) => copy(onFinish = _ => onCancel())
    case Message.Failure(onFail)  => copy(onFinish = _ => onFail())
    case Message.Count(onCount) =>
      onCount(count)
      this
  }
  def finish(): Unit = onFinish(count)
}

class ConversionWorker(
    context: ActorContext[_],
    url: String,
    result: Path,
    onDone: Long => Unit,
    onFail: () => Unit
) {
  implicit val as: ActorSystem[Nothing] = context.system
  implicit val ec = as.executionContext

  private val items =
    Source
      .futureSource(
        Http()
          .singleRequest(HttpRequest(HttpMethods.GET, url))
          .map(response =>
            if (response.entity.contentType.mediaType == MediaTypes.`text/csv`)
              response.entity.dataBytes
            else
              Source.failed(
                new Exception(
                  s"Incorrect content-type: ${response.entity.contentType}"
                )
              )
          )
      )
      .via(CsvParsing.lineScanner())
      .via(CsvToMap.toMap())
      .map(Json.toJson(_))
      .map(Json.stringify(_))
      .intersperse("[", ",\n", "]")
      .map(ByteString(_))

  private val commands = ActorSource.actorRef[Message](
    { case Message.Complete => () },
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

  private val commandActor =
    items
      .alsoToMat(counter)(Keep.right)
      .to(FileIO.toPath(result))
      .run()

  def cancel(onCancel: () => Unit): Unit =
    commandActor ! Message.Cancel(onCancel)
  def currentCount(onCount: Long => Unit): Unit =
    commandActor ! Message.Count(onCount)
}
