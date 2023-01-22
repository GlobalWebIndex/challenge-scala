import com.typesafe.config.ConfigFactory
import com.typesafe.config.{Config => TSConfig}
import controllers.CheckController
import controllers.CsvToJsonController
import conversion.FileSaver
import conversion.HttpConversion
import conversion.UUIDNamer
import models.TaskId
import pool.WorkerFactory
import pool.WorkerPool
import pool.dependencies.Config

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller

import java.nio.file.Paths
import scala.io.StdIn

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
  )(implicit actorContext: ActorContext[_]): Route = {
    implicit val ec = actorContext.executionContext
    val log = actorContext.log

    val saver =
      new FileSaver(
        log,
        Paths.get(config.getString("csvToJson.resultDirectory"))
      )
    val workerFactory = WorkerFactory.default(HttpConversion, saver)
    val conversionPool =
      new WorkerPool(
        Config.fromConf(config.getConfig("conversion")),
        log,
        workerFactory,
        saver,
        UUIDNamer,
        "conversionPool"
      )

    val checkController = new CheckController(actorContext.log)
    val csvToJsonController =
      new CsvToJsonController(config, log, conversionPool)

    concat(
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
  }

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val host = config.getString("csvToJson.host")
    val port = config.getInt("csvToJson.port")
    val server = ActorSystem(
      Behaviors.setup[Option[Http.ServerBinding]] { ctx =>
        implicit val system = ctx.system
        implicit val ec = ctx.executionContext
        ctx.pipeToSelf(
          Http()
            .newServerAt(host, port)
            .bind(createRoute(config)(ctx))
        )(_.toOption)
        Behaviors.receiveMessage {
          case None => Behaviors.same
          case Some(binding) =>
            ctx.log.info("Server started")
            Behaviors.receiveMessage {
              case None =>
                binding.unbind()
                ctx.log.info("Server stopped")
                Behaviors.stopped
              case Some(_) => Behaviors.same
            }
        }
      },
      "challenge"
    )
    StdIn.readLine()
    server ! None
  }
}
