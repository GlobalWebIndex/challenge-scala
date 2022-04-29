package com.gwi.karelsk

import org.scalatest.OptionValues._
import org.scalatest.matchers.Matcher
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json.{JsValue, JsonParser}

import java.net.URI
import java.nio.file.Path

class TaskSpec extends AnyWordSpec with Matchers {
  import Task._

  def beJson(s: String): Matcher[JsValue] = be (JsonParser(s))
  val terminal = Symbol("terminal")

  "Task" must {
    implicit val resultLoc: Id => String = id => s"result-$id"

    "first be scheduled" in {
      val task = Task(42, new URI("a"))
      task.id shouldBe 42
      task.uri shouldBe new URI("a")
      task should not be terminal
      task.cancel shouldBe defined
      task.progress shouldBe (0, 0L)
      task.toJson should beJson("""{ "id": 42, "state": "SCHEDULED", "source": "a" }""")
    }

    "can be canceled before running" in {
      val task = Task(43, new URI("b")).cancel.value
      task.id shouldBe 43
      task shouldBe terminal
      task.cancel should not be defined
      task.progress shouldBe (0, 0L)
      task.toJson should beJson("""{ "id": 43, "state": "CANCELED", "lines": 0, "rate": 0.0 }""")
    }

    "can be running after scheduling" in {
      val task = Task(44, new URI("c")).run(Path.of("tmp-44.json"))
      task.id shouldBe 44
      task should not be terminal
      task.cancel shouldBe defined
      task.progress shouldBe (0, 0L)
      task.result shouldBe Path.of("tmp-44.json")
      task.toJson should beJson("""{ "id": 44, "state": "RUNNING", "lines": 0, "rate": 0.0 }""")
    }

    "can advance progress while running" in {
      val task = Task(45, new URI("d")).run(Path.of("tmp-45.json"))
        .advanceTo(1001, 2000)

      task.id shouldBe 45
      task should not be terminal
      task.cancel shouldBe defined
      task.progress shouldBe (1001, 2000L)
      task.result shouldBe Path.of("tmp-45.json")
      task.toJson should beJson("""{ "id": 45, "state": "RUNNING", "lines": 1001, "rate": 500.5 }""")
    }

    "can be canceled while running" in {
      val task = Task(46, new URI("e")).run(Path.of("tmp-46.json"))
        .advanceTo(1001, 4000).cancel.value

      task.id shouldBe 46
      task shouldBe terminal
      task.cancel should not be defined
      task.progress shouldBe (1001, 4000L)
      task.toJson should beJson("""{ "id": 46, "state": "CANCELED", "lines": 1001, "rate": 250.25 }""")
    }

    "can be finished after running" in {
      val task = Task(47, new URI("f")).run(Path.of("tmp-47.json"))
        .advanceTo(1001, 1000).finish

      task.id shouldBe 47
      task shouldBe terminal
      task.cancel should not be defined
      task.progress shouldBe (1001, 1000L)
      task.toJson should beJson("""{ "id": 47, "state": "DONE", "lines": 1001, "rate": 1001.0, "result": "result-47" }""")
    }

    "can fail during running" in {
      val error = new Error("some error when running")
      val task = Task(48, new URI("g")).run(Path.of("tmp-48.json"))
        .advanceTo(1001, 8000).fail(error)

      task.id shouldBe 48
      task shouldBe terminal
      task.cancel should not be defined
      task.progress shouldBe (1001, 8000L)
      task.cause shouldBe error
      task.toJson should beJson(s"""{ "id": 48, "state": "FAILED", "lines": 1001, "rate": 125.125, "cause": "${error.toString}" }""")
    }

    "can fail even before running" in {
      val error = new Error("some error before running")
      val task = Task(49, new URI("h")).fail(error)

      task.id shouldBe 49
      task shouldBe terminal
      task.cancel should not be defined
      task.progress shouldBe (0, 0L)
      task.cause shouldBe error
      task.toJson should beJson(s"""{ "id": 49, "state": "FAILED", "lines": 0, "rate": 0.0, "cause": "${error.toString}" }""")
    }
  }
}
