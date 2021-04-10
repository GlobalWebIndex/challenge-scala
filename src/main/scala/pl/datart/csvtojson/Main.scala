package pl.datart.csvtojson

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import cats.effect._
import cats.effect.std._
import cats.effect.unsafe.IORuntime.global
import pl.datart.csvtojson.http.HttpRoutes
import pl.datart.csvtojson.model._
import pl.datart.csvtojson.service._
import pl.datart.csvtojson.util.FAdapter._
import pl.datart.csvtojson.util._

import scala.concurrent._
import scala.util._

@SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
object Main {
  private implicit val system: ActorSystem            = ActorSystem("main-system")
  private implicit val fAdapter: FAdapter[IO, Future] = FAdapterIOFGlobal.adapter

  private val host = "localhost"
  private val port = 8080

  def main(args: Array[String]): Unit = {
    val binding =
      for {
        tasks        <- Ref[IO].of(Map.empty[TaskId, Task])
        semaphore    <- Semaphore[IO](2)
        tasksService  = new TaskServiceImpl[IO](tasks, StatsComposerImpl)
        taskRunner    = new TaskRunnerImpl[IO](tasksService, semaphore)(IO.asyncForIO)
        taskScheduler = new TaskSchedulerImpl[IO](tasks, semaphore, tasksService, taskRunner)(
                          IO.asyncForIO,
                          system.getDispatcher
                        )
        httpRoutes    = new HttpRoutes(taskScheduler, tasksService)
        binding      <- IO.fromFuture {
                          IO {
                            Http()(system)
                              .newServerAt(host, port)
                              .bind(httpRoutes.routes)
                          }
                        }
      } yield binding

    binding
      .unsafeToFuture()(global)
      .onComplete {
        case scala.util.Success(binding) =>
          val address = binding.localAddress
          system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
        case Failure(ex)                 =>
          system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
          system.terminate()
      }(system.getDispatcher)
  }
}
