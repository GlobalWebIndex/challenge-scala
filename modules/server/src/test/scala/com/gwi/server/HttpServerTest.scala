package com.gwi.server

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.headers.{ContentDispositionTypes, `Content-Disposition`}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink}
import akka.testkit.TestDuration
import com.google.inject.{AbstractModule, Guice}
import com.gwi.database.model.memory.{Task, TaskState}
import com.gwi.database.model.memory.dao.TaskRepository
import com.gwi.database.model.persistent.dao.JsonLineRepository
import com.gwi.server.response.{ErrorResponse, ReadinessResponse}
import com.gwi.service.TaskService
import com.gwi.service.client.HttpClient
import com.gwi.service.dto.TaskDto
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.{DefaultJsonProtocol, JsValue}

import java.nio.file.Paths
import scala.concurrent.{Await, ExecutionContext, Future}
import java.util.UUID
import scala.concurrent.duration._

class HttpServerTest
    extends AnyWordSpecLike
    with Matchers
    with ScalatestRouteTest
    with BeforeAndAfterEach
    with SprayJsonSupport
    with DefaultJsonProtocol {

  override def beforeEach(): Unit = {
    val jsonLineRepository = injector.getInstance(classOf[JsonLineRepository])
    import jsonLineRepository.session.profile.api._
    jsonLineRepository.session.db.run(jsonLineRepository.jsonLine.delete.transactionally)
    val taskRepository = injector.getInstance(classOf[TaskRepository])
    taskRepository.deleteAll()
  }

  override def afterEach(): Unit = {
    val jsonLineRepository = injector.getInstance(classOf[JsonLineRepository])
    import jsonLineRepository.session.profile.api._
    jsonLineRepository.session.db.run(jsonLineRepository.jsonLine.delete.transactionally)
    val taskRepository = injector.getInstance(classOf[TaskRepository])
    taskRepository.deleteAll()
  }

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(5.seconds.dilated)

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
      taskRepository.upsert(Task(taskId, 100, 100, TaskState.DONE))
      Delete(s"/task/$taskId") ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
        contentType should ===(ContentTypes.`application/json`)
        responseAs[ErrorResponse].message.isEmpty shouldBe false
      }
    }
  }

  "task endpoints" should {
    "get all tasks" in {
      val routes = injector.getInstance(classOf[HttpServer]).buildRoute()
      val taskRepository = injector.getInstance(classOf[TaskRepository])
      val taskId = UUID.randomUUID()
      taskRepository.upsert(Task(taskId, 100, 100, TaskState.DONE))
      Get("/task") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType should ===(ContentTypes.`application/json`)
        val response = responseAs[Seq[TaskDto]]
        response.size shouldBe 1
        response.headOption.map(_.id) should contain(taskId)
        response.headOption.map(_.state) should contain(TaskState.DONE.toString)
        response.headOption.flatMap(_.result) should not be empty
      }
    }

    "stream task update until is in final state" in {
      val routes = injector.getInstance(classOf[HttpServer]).buildRoute()
      val taskRepository = injector.getInstance(classOf[TaskRepository])
      val taskId = UUID.randomUUID()
      taskRepository.upsert(Task(taskId, 100, 100, TaskState.RUNNING))
      akka.pattern.after(1.second)(Future.successful(taskRepository.upsert(Task(taskId, 100, 100, TaskState.DONE))))
      Get(s"/task/$taskId") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType should ===(ContentTypes.`application/json`)
        val response = responseAs[Seq[TaskDto]]
        response.size shouldBe 2

        response.headOption.map(_.id) should contain(taskId)
        response.headOption.map(_.state) should contain(TaskState.RUNNING.toString)
        response.headOption.flatMap(_.result) shouldBe empty

        response.lastOption.map(_.id) should contain(taskId)
        response.lastOption.map(_.state) should contain(TaskState.DONE.toString)
        response.lastOption.flatMap(_.result) should not be empty
      }
    }

    "cancel a running task" in {
      val routes = injector.getInstance(classOf[HttpServer]).buildRoute()
      val taskRepository = injector.getInstance(classOf[TaskRepository])
      val taskId = UUID.randomUUID()
      taskRepository.upsert(Task(taskId, 100, 100, TaskState.RUNNING))
      Delete(s"/task/$taskId") ~> routes ~> check {
        status shouldBe StatusCodes.NoContent
        taskRepository.get(taskId).map(_.state) should contain(TaskState.CANCELED)
      }
    }

    "get json lines of a completed task" in {
      val routes = injector.getInstance(classOf[HttpServer]).buildRoute()
      val taskService = injector.getInstance(classOf[TaskService])
      val taskId = taskService.createTask("randomUrl")
      val taskInfoResult = taskService.getTaskInfo(taskId)
      taskInfoResult.isLeft shouldBe true
      val doneTask = Await.result(
        taskInfoResult.left
          .getOrElse(throw new RuntimeException)
          .takeWhile(task => !task.map(_.state).contains(TaskState.DONE.toString), inclusive = true)
          .runWith(Sink.last),
        10.seconds
      )
      doneTask.map(_.state) should contain(TaskState.DONE.toString)
      doneTask.flatMap(_.result) should not be empty
      Get(s"/task/result/$taskId") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType should ===(ContentTypes.`application/json`)
        val contentDispositionHeaderOpt = header[`Content-Disposition`]
        contentDispositionHeaderOpt should not be empty
        contentDispositionHeaderOpt.map(_.dispositionType) should contain(ContentDispositionTypes.attachment)
        contentDispositionHeaderOpt.flatMap(_.params.get("filename")) should not be empty
        val response = responseAs[Seq[JsValue]]
        println(response.size)
        doneTask.map(_.linesProcessed) should contain(response.size)
      }
    }
  }

}
