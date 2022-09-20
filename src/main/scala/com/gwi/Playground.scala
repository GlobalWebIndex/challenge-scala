import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import scala.concurrent.Future

object Playground extends App {
  implicit val system: ActorSystem = ActorSystem("QuickStart")
  import akka.actor.typed.ActorRef
  import akka.stream.OverflowStrategy
  import akka.stream.scaladsl.{Sink, Source}
  import akka.stream.typed.scaladsl.ActorSource

  trait Protocol
  case class Message(msg: String) extends Protocol
  case object Complete            extends Protocol
  case class Fail(ex: Exception)  extends Protocol

  val source: Source[Protocol, ActorRef[Protocol]] = ActorSource.actorRef[Protocol](
    completionMatcher = { case Complete =>
    },
    failureMatcher = { case Fail(ex) =>
      ex
    },
    bufferSize = 8,
    overflowStrategy = OverflowStrategy.fail
  )

  val ref = source.collect { case Message(msg) =>
    msg
  }
    .to(Sink.foreach(println))
    .run()

  ref ! Message("msg1")
// ref ! "msg2" Does not compile
}
