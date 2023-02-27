package cz.vlasec.gwi.csvimport

import akka.actor.ActorSystem
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
 * Sourceror does the dark magic of opening HTTP portals so that others don't have to.
 * It works asynchronously. It initiates HTTP connection with given URL, processes redirects,
 * and in the end, it calls one of the provided callbacks to leave the heavy lifting to others.
 * As an esteemed magician, the sourceror can clone itself, but prefers not to keep its clones around.
 */
object Sourceror {
  type SourceConsumerRef = ActorRef[Option[Source[ByteString, Any]]]
  sealed trait SourcerorCommand
  final case class InitiateHttp(url: String, replyTo: SourceConsumerRef) extends SourcerorCommand
  private case class HandleResponse(response: Try[HttpResponse]) extends SourcerorCommand
  private case class HandleRedirect(newUrl: String) extends SourcerorCommand
  private case object Fail extends SourcerorCommand
  private case class Succeed(source: Source[ByteString, Any]) extends SourcerorCommand

  def apply(): Behavior[SourcerorCommand] = idle()

  private def idle(clone: Boolean = false): Behavior[SourcerorCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case InitiateHttp(url, replyTo) =>
        busy(url, replyTo)
      case x =>
        context.log.warn(s"Invalid command: $x"); Behaviors.same
    }
  }

  private def busy(url: String, replyTo: SourceConsumerRef, redirects: Int = 0, clone: Boolean = false)
  : Behavior[SourcerorCommand] = Behaviors.setup { context =>
    implicit val classicSystem: ActorSystem = context.system.classicSystem
    implicit val executionContext: ExecutionContext = context.system.executionContext

    Http().singleRequest(Get(url))
      .onComplete(context.self ! HandleResponse(_))
    Behaviors.receiveMessage {
      case InitiateHttp(aUrl, aReplyTo) =>
        context.spawnAnonymous[SourcerorCommand](idle(clone = true)) ! InitiateHttp(aUrl, aReplyTo)
        Behaviors.same
      case HandleResponse(response) =>
        handleResponse(response, url, context)
        Behaviors.same
      case HandleRedirect(newUrl) =>
        busy(newUrl, replyTo, redirects + 1)
      case Succeed(source) =>
        replyTo ! Some(source)
        if (clone) Behaviors.stopped else idle()
      case Fail =>
        replyTo ! None
        if (clone) Behaviors.stopped else idle()
      case x =>
        context.log.warn(s"Invalid command: $x"); Behaviors.same
    }
  }

  private def handleResponse(tried: Try[HttpResponse], url: String, context: ActorContext[SourcerorCommand]): Unit = tried match {
    case Success(response) =>
      implicit val materializer: Materializer = Materializer(context)
      if (response.status.isFailure()) {
        response.entity.discardBytes()
        context.log.warn(s"HTTP request failed with status ${response.status}.")
        context.self ! Fail
      } else if (response.status.isRedirection()) {
        // This monstrosity might have a better solution. However:
        // - Spray HTTP is aging like a fine milk, with only Scala 2.11 support and last version from 2016
        // - Gigahorse doesn't seem to be very inter-operable with Akka Streams
        // - other options might exist, but Googling things like Akka Http isRedirection or FollowRedirect find nothing
        // Thus, I decided to at least come up with a working solution that looks like a dumpster fire.
        response.entity.discardBytes()
        val baseUrl = url.replaceAll("^(https?://[^/]+).*$", "$1")
        response.headers.find(_.name == "Location").map(_.value).map(redir =>
          if (redir.startsWith("/")) baseUrl + redir else redir
        ) match {
          case Some(redirUrl) =>
            context.self ! HandleRedirect(redirUrl)
          case None =>
            context.log.warn(s"Failed to follow redirections in URL: $url")
            context.self ! Fail
        }
      } else {
        context.self ! Succeed(response.entity.dataBytes)
      }
    case Failure(exception) =>
      context.log.error(s"Failed to start downloading CSV, because of exception thrown:", exception)
      context.self ! Fail
  }
}
