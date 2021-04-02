package pl.datart.csvtojson

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.unmarshalling._
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import pl.datart.csvtojson.model.JsonFormats._
import pl.datart.csvtojson.model._
import spray.json.DefaultJsonProtocol._

import java.util.UUID
import scala.io.Source


class MainIntegrationTest extends AsyncFunSpec with Matchers with Directives {

  private implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty, "SingleRequest")
  private val uuidRegex = "([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})"

  describe("main") {
    it("runs server HTTP server properly") {
      Main.main(Array())

      val rawUri = RawUri(uri = Source.getClass.getResource("/example_file.csv").getPath)

      val assertionF = for {
        uri <- Marshal(rawUri).to[RequestEntity]
        enqueueResponse <- Http().singleRequest(Post("http://localhost:8080/task", uri))
        taskId <- Unmarshal(enqueueResponse).to[TaskId]
        listTasksResponse <- Http().singleRequest(Get("http://localhost:8080/task"))
        tasks <- Unmarshal(listTasksResponse).to[Iterable[TaskId]]
        cancelResponse <- Http().singleRequest(Delete(s"http://localhost:8080/task/${UUID.randomUUID().toString}"))
        } yield
        (
          taskId.taskId.matches(uuidRegex) &&
            tasks.exists(_.taskId === taskId.taskId) &&
            cancelResponse.status === NotFound
        ) shouldBe true

      assertionF
        .onComplete(_ => system.terminate())

      assertionF
    }
  }
}
