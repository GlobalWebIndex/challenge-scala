package pl.datart.csvtojson

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import cats.effect._
import cats.effect.unsafe.IORuntime.global
import pl.datart.csvtojson.http.HttpRoutes
import pl.datart.csvtojson.service.TaskEnqueuerImpl
import pl.datart.csvtojson.util._

import scala.concurrent._
import scala.util._

object Main {
  private implicit val system: ActorSystem[Unit]          = ActorSystem(Behaviors.empty, "main-system")
  private implicit val fConverter: FConverter[IO, Future] = new FConverter.FConverterIOF()(global)

  private val host = "localhost"
  private val port = 8080

  private val taskEnqueuer               = new TaskEnqueuerImpl[IO]()
  private val httpServer: HttpRoutes[IO] = new HttpRoutes(taskEnqueuer)

  def main(args: Array[String]): Unit = {
    val bindingFuture = Http()
      .newServerAt(host, port)
      .bind(httpServer.routes)

    bindingFuture.onComplete {
      case scala.util.Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex)                 =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }(system.executionContext)
  }
}
