package gwi

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{ ExceptionHandler => _, _ }
import akka.util.Timeout
import java.nio.file.Path
import org.slf4j.LoggerFactory
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._

object Main {
  private val log = LoggerFactory.getLogger(classOf[Main.type])

  def main(args: Array[String]): Unit = {
    val guardian = Behaviors.setup[Try[ServerBinding]] { context =>
      implicit val system: ActorSystem[_] = context.system
      implicit val timeout: Timeout = 10.seconds
      import system.executionContext

      val storage = Storage(Path.of("/tmp/challenge-scala"))

      val factory = Worker.factory(system, storage)
      val manager = context.spawn(Manager(factory), name = "manager")
      val service = TaskService(manager, storage)

      val endpoints = new TaskEndpoints(service)

      context.pipeToSelf(startHttpServer(system, seal(endpoints.route)))(identity)
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

  def seal(route: Route): Route = Route.seal(route)(exceptionHandler = ExceptionHandler())
}
