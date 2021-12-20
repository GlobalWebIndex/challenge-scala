package com.krykowski.api

import cats.effect.{IO, Timer}
import com.krykowski.HttpServer
import com.krykowski.repository.TaskInMemoryRepository
import com.krykowski.service.TaskServiceImpl
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.{Method, Request, Status, Uri}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class TaskApiServerSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with Eventually {

  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit private val cs = IO.contextShift(global)
  implicit private val cf = IO.ioConcurrentEffect

  private lazy val client = BlazeClientBuilder[IO](global).resource

  private lazy val app = ItConfig.appConfig

  private lazy val urlStart =
    s"http://${app.host}:${app.port}"

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(5, Seconds)),
    interval = scaled(Span(100, Millis))
  )

  val repository = new TaskInMemoryRepository
  val service = new TaskServiceImpl(repository)

  override def beforeAll(): Unit = {
    HttpServer.create(app, service).unsafeRunAsyncAndForget()
    eventually {
      client
        .use(_.statusFromUri(Uri.unsafeFromString(s"$urlStart/task")))
        .unsafeRunSync() shouldBe Status.Ok
    }
    ()
  }

  "Task Api server" should {
    "return an empty task list" in {
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(
          s"$urlStart/task"
        )
      )
      val response = client.use(_.expect[String](request)).unsafeRunSync()
      response should include("[]")
    }
  }
}
