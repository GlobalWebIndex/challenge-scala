package com.gwi.server

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import com.google.inject.{AbstractModule, Guice}
import com.gwi.database.model.memory.{Task, TaskState}
import com.gwi.database.model.memory.dao.TaskRepository
import com.gwi.server.response.{ErrorResponse, ReadinessResponse}
import com.gwi.service.client.HttpClient
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.DefaultJsonProtocol

import java.nio.file.Paths
import scala.concurrent.ExecutionContext
import java.util.UUID

class HttpServerTest
    extends AnyWordSpecLike
    with Matchers
    with ScalatestRouteTest
    with SprayJsonSupport
    with DefaultJsonProtocol {

  private val injector = Guice.createInjector(new AbstractModule() {
    override def configure(): Unit = {
      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.getCsvFile(anyString())).thenReturn({
        val resourceUrl = getClass.getResource("/Lottery_Powerball_Winning_Numbers__Beginning_2010.csv")
        val sourceFilePath = Paths.get(resourceUrl.getPath)
        FileIO
          .fromPath(sourceFilePath)
      })

      bind(classOf[HttpClient]).toInstance(mockHttpClient)
      bind(classOf[ActorSystem]).toInstance(system)
      bind(classOf[Materializer]).toInstance(Materializer(system))
      bind(classOf[ExecutionContext]).toInstance(system.dispatcher)
    }
  })

  "ready endpoint" should {
    "return ready" in {
      val routes = injector.getInstance(classOf[HttpServer]).buildRoute()
      Get("/ready") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType should ===(ContentTypes.`application/json`)
        responseAs[ReadinessResponse].isReady shouldBe true
      }
    }
  }

  "error responses" should {
    "return json response body in method not allowed response" in {
      val routes = injector.getInstance(classOf[HttpServer]).buildRoute()
      Post("/ready") ~> routes ~> check {
        status shouldBe StatusCodes.MethodNotAllowed
        contentType should ===(ContentTypes.`application/json`)
        responseAs[ErrorResponse].message.isEmpty shouldBe false
      }
    }

    "return json response body in not found response" in {
      val routes = injector.getInstance(classOf[HttpServer]).buildRoute()
      Get(s"/task/${UUID.randomUUID()}") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        contentType should ===(ContentTypes.`application/json`)
        responseAs[ErrorResponse].message.isEmpty shouldBe false
      }
    }

    "return json response body in not found task lines" in {
      val routes = injector.getInstance(classOf[HttpServer]).buildRoute()
      Get(s"/task/result/${UUID.randomUUID()}") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        contentType should ===(ContentTypes.`application/json`)
        responseAs[ErrorResponse].message.isEmpty shouldBe false
      }
    }

    "return json response body in not found task in cancel request" in {
      val routes = injector.getInstance(classOf[HttpServer]).buildRoute()
      Delete(s"/task/${UUID.randomUUID()}") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
        contentType should ===(ContentTypes.`application/json`)
        responseAs[ErrorResponse].message.isEmpty shouldBe false
      }
    }

    "return json response body in non cancelable task in cancel request" in {
      val routes = injector.getInstance(classOf[HttpServer]).buildRoute()
      val taskRepository = injector.getInstance(classOf[TaskRepository])
      val taskId = UUID.randomUUID()
      taskRepository.upsert(Task(taskId, 100, 100, TaskState.DONE, Some("resultUrl")))
      Delete(s"/task/$taskId") ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        contentType should ===(ContentTypes.`application/json`)
        responseAs[ErrorResponse].message.isEmpty shouldBe false
      }
    }
  }

}
