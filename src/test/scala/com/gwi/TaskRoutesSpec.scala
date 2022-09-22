package com.gwi

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import akka.actor.testkit.typed.scaladsl.TestProbe

class TaskRoutesSpec extends AnyWordSpec with Matchers with ScalaFutures with ScalatestRouteTest with JsonSupport {

  lazy val testKit         = ActorTestKit()
  implicit def typedSystem = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem =
    testKit.system.classicSystem

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  override def afterAll(): Unit = testKit.shutdownTestKit()

  val taskRepository = testKit.spawn(TaskRepository())
  val routes         = new TaskRoutes(taskRepository).taskRoutes

  "TaskRoutes" should {

    "return no task if no present (GET /task)" in {
      val request = HttpRequest(uri = "/task")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        entityAs[String] should ===("""[]""")
      }
    }

    "return the created task id (POST /task)" in {
      val task       = TaskRepository.Task(Uri("http://www.ietf.org/rfc/rfc2396.txt"))
      val taskEntity = Marshal(task).to[MessageEntity].futureValue

      val create = Post("/task").withEntity(taskEntity)
      create ~> routes ~> check {
        status should ===(StatusCodes.Created)
        contentType should ===(ContentTypes.`application/json`)
        val entity = entityAs[TaskRepository.TaskCreated]
        entity should ===(TaskRepository.TaskCreated(0))
      }
  
    }

    "return all created tasks  (GET /task)" in {
      val getAll = HttpRequest(uri = "/task")
      getAll ~> routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        entityAs[String] should ===("""[0]""")
      }
    }

    "return the status  (GET /task/0)" in {

      val get = HttpRequest(uri = "/task/0")
      get ~> routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        entityAs[String] should ===("""[{"avgLinesCnt":0,"id":0,"linesCnt":0,"status":"Failed","target":"/tmp/0.json"}]""")
      }
    }

    "return the created task id with nonexisttent uri (POST /task)" in {
      val task       = TaskRepository.Task(Uri("http://www.dummy.com"))
      val taskEntity = Marshal(task).to[MessageEntity].futureValue

      val create = Post("/task").withEntity(taskEntity)
      create ~> routes ~> check {
        status should ===(StatusCodes.InternalServerError)
        println( entityAs[String])
      }
     }

  }
}
