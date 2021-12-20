package com.krykowski.api

import cats.effect.IO
import com.krykowski.model.{Running, Scheduled, Task}
import com.krykowski.service.TaskService
import org.http4s.{Request, Response, Status}
import org.http4s.dsl.io.GET
import org.http4s.implicits.{http4sKleisliResponseSyntaxOptionT, http4sLiteralsSyntax}
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec


class TaskApiSpec extends AnyWordSpec with MockFactory with Matchers {

  private val taskService = stub[TaskService]
  private val taskApi = new TaskApi(taskService).routes

  "Task Api" should {
    "return message when forex API is not available" in {
      val id1 = 1
      val task1 = Task(Some(id1), Scheduled, "/someUri")
      val id2 = 2
      val task2 = Task(Some(id2), Running, "/someOtherUri")
      val tasks = List(task1, task2)
      (taskService.getAllTasks _).when().returns(tasks)

      val response = serve(Request[IO](GET, uri"/task"))
      response.status shouldBe Status.Ok
      response.as[String].unsafeRunSync() shouldBe
        s"""[{"id":${task1.id.get},"status":"${task1.status}","jsonFileUri":"${task1.jsonFileUri}"},{"id":${task2.id.get},"status":"${task2.status}","jsonFileUri":"${task2.jsonFileUri}"}]"""
    }
  }
  private def serve(request: Request[IO]): Response[IO] = {
    taskApi.orNotFound(request).unsafeRunSync()
  }
}
