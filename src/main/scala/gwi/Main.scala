package gwi

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.slf4j.LoggerFactory
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._

object Main {
  private val log = LoggerFactory.getLogger(classOf[Main.type])

  def main(args: Array[String]): Unit = {
    val guardian = Behaviors.setup[Try[ServerBinding]] { context =>
      val system = context.system
      import system.executionContext

      val route: Route = complete("ok")

      context.pipeToSelf(startHttpServer(system, Route.seal(route)))(identity)
      Behaviors.receiveMessage {
        case Success(ServerBinding(localAddress)) =>
          log.info("Server has started ({})", localAddress)
          Behaviors.empty
        case Failure(cause) =>
          log.error("Server has failed to start", cause)
          Behaviors.stopped
      }
    }

    ActorSystem(guardian, name = "challenge-scala")
    ()
  }

  def startHttpServer(system: ActorSystem[_], route: Route)(implicit ec: ExecutionContext): Future[ServerBinding] = {
    implicit val sys: ActorSystem[_] = system
    Http(system)
      .newServerAt(interface = "127.0.0.1", port = 8080)
      .bind(route)
      .map(_.addToCoordinatedShutdown(60.seconds))
  }
}
