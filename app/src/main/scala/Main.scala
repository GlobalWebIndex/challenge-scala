import com.typesafe.config.ConfigFactory
import com.typesafe.config.{Config => TSConfig}
import controllers.CheckController
import controllers.CsvToJsonController
import conversion.FileSaver
import conversion.HttpConversion
import conversion.UUIDNamer
import models.TaskId
import org.slf4j.LoggerFactory
import pool.WorkerFactory
import pool.WorkerPool
import pool.dependencies.Config

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.util.Timeout

import java.nio.file.Path
import java.nio.file.Paths
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.StdIn

/** Program entry point */
object Main {

  private implicit val uriUnmarshaller: FromRequestUnmarshaller[Uri] =
    implicitly[FromRequestUnmarshaller[String]].map(Uri(_))

  private def taskResultUrl(requestUri: Uri, taskId: TaskId): String = {
    val link = s"/task/result/${taskId.id}"
    if (requestUri.isAbsolute) Uri(link).resolvedAgainst(requestUri).toString()
    else link
  }

  private def createRoute(
      config: TSConfig
  )(implicit
      actorContext: ActorContext[_]
  ): (WorkerPool[TaskId, Uri, Path], Route) = {
    implicit val ec = actorContext.executionContext
    implicit val actorSystem = actorContext.system
    val saver =
      new FileSaver(
        LoggerFactory.getLogger("FileSaver"),
        Paths.get(config.getString("csvToJson.resultDirectory"))
      )
    val conversionPool =
      WorkerPool(
        Config.fromConf(config.getConfig("pool")),
        LoggerFactory.getLogger("ConversionPool"),
        WorkerFactory(HttpConversion, saver),
        saver,
        UUIDNamer,
        "conversionPool"
      )

    val checkController = new CheckController(actorContext.log)
    val csvToJsonController =
      new CsvToJsonController(
        config,
        LoggerFactory.getLogger("CsvToJsonController"),
        conversionPool
      )

    val route = concat(
      (get & path("check")) {
        checkController.check
      },
      (post & path("task") & decodeRequest & entity(as[Uri])) {
        csvToJsonController.createTask(_)
      },
      (get & path("task") & extractUri) { uri =>
        csvToJsonController.listTasks(taskResultUrl(uri, _))
      },
      (get & path("task" / TaskId.Matcher) & extractUri) { (taskId, uri) =>
        csvToJsonController.taskDetails(taskId, taskResultUrl(uri, _))
      },
      (delete & path("task" / TaskId.Matcher)) {
        csvToJsonController.cancelTask(_)
      },
      (get & path("task" / "result" / TaskId.Matcher)) {
        csvToJsonController.taskResult(_)
      }
    )
    (conversionPool, route)
  }

  private sealed trait Message
  private object Message {
    object StartFailure extends Message
    final case class Stopping(replyTo: ActorRef[Done]) extends Message
    final case class Stopped(replyTo: ActorRef[Done]) extends Message
  }

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val host = config.getString("csvToJson.host")
    val port = config.getInt("csvToJson.port")
    val server = ActorSystem(
      Behaviors
        .setup[Either[Message, Http.ServerBinding]] { ctx =>
          implicit val system = ctx.system
          implicit val ec = ctx.executionContext
          val (pool, route) = createRoute(config)(ctx)
          ctx.pipeToSelf(Http().newServerAt(host, port).bind(route))(
            _.toEither.left.map(_ => Message.StartFailure)
          )
          Behaviors.receiveMessage {
            case Left(_) =>
              ctx.log.info("Server failed to start")
              Behaviors.stopped
            case Right(binding) =>
              ctx.log.info(s"Server started at $host:$port")
              Behaviors.receiveMessage {
                case Left(Message.StartFailure) => Behaviors.same
                case Left(Message.Stopping(replyTo)) =>
                  binding.unbind()
                  ctx.log.info("Server stopped")
                  ctx.pipeToSelf(pool.cancelAll())(_ =>
                    Left(Message.Stopped(replyTo))
                  )
                  Behaviors.same
                case Left(Message.Stopped(replyTo)) =>
                  ctx.log.info("All tasks cancelled")
                  replyTo ! Done
                  Behaviors.stopped
                case Right(_) => Behaviors.same
              }
          }
        },
      "challenge"
    )
    StdIn.readLine()
    Await.result(
      server.ask[Done](r => Left(Message.Stopping(r)))(
        Timeout(Duration(30, "s")),
        server.scheduler
      ),
      Duration(1, "m")
    )
  }
}
