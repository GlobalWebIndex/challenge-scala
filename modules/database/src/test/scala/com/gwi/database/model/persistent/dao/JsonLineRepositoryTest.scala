package com.gwi.database.model.persistent.dao

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import akka.testkit.TestKit
import com.gwi.database.model.persistent.JsonLine
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.wordspec.AsyncWordSpecLike

import java.util.UUID

class JsonLineRepositoryTest
    extends TestKit(ActorSystem("JsonLineRepositorySpec"))
    with AsyncWordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  val jsonLineRepository = new JsonLineRepository()

  override def afterEach(): Unit = {
    import jsonLineRepository.session.profile.api._
    jsonLineRepository.session.db.run(jsonLineRepository.jsonLine.delete)
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "JsonLineRepository" should {
    "find all completed tasks" in {
      val taskId1 = UUID.randomUUID()
      val taskId2 = UUID.randomUUID()
      for (_ <- 1 to 10) {
        jsonLineRepository.create(JsonLine(taskId1, 100, "{\"element\":\"value\"}"))
        jsonLineRepository.create(JsonLine(taskId2, 100, "{\"element\":\"value\"}", isComplete = true))
      }

      val result = jsonLineRepository
        .findAllTasks()
        .runWith(Sink.collection[DoneTaskFromJsonLines, List[DoneTaskFromJsonLines]])

      result.map(taskList => {
        assert(taskList.size == 1)
        assert(taskList.map(_.taskId).distinct.headOption.contains(taskId2))
      })
    }

    "get all lines of completed tasks" in {
      val taskId1 = UUID.randomUUID()
      val taskId2 = UUID.randomUUID()
      for (_ <- 1 to 10) {
        jsonLineRepository.create(JsonLine(taskId1, 100, "{\"element\":\"value\"}"))
        jsonLineRepository.create(JsonLine(taskId2, 100, "{\"element\":\"value\"}", isComplete = true))
      }

      val result1 = jsonLineRepository
        .getJsonLines(taskId1)
        .runWith(Sink.collection[JsonLine, List[JsonLine]])

      val result2 = jsonLineRepository
        .getJsonLines(taskId2)
        .runWith(Sink.collection[JsonLine, List[JsonLine]])

      result1.map(lines => assert(lines.isEmpty))
      result2.map(lines => {
        assert(lines.size == 10)
        val taskIdList = lines.map(_.taskId).distinct
        assert(taskIdList.size == 1)
        assert(taskIdList.headOption.contains(taskId2))
      })
    }

    "mark lines of a task completed" in {
      val taskId = UUID.randomUUID()
      for (_ <- 1 to 10) {
        jsonLineRepository.create(JsonLine(taskId, 100, "{\"element\":\"value\"}"))
      }

      val result1 = jsonLineRepository
        .getJsonLines(taskId)
        .runWith(Sink.collection[JsonLine, List[JsonLine]])

      result1.map(lines => assert(lines.isEmpty))

      jsonLineRepository.markJsonLinesCompleted(taskId)
      val result2 = jsonLineRepository
        .getJsonLines(taskId)
        .runWith(Sink.collection[JsonLine, List[JsonLine]])
      result2.map(lines => {
        assert(lines.size == 10)
        val taskIdList = lines.map(_.taskId).distinct
        assert(taskIdList.size == 1)
        assert(taskIdList.headOption.contains(taskId))
      })
    }
  }

}
