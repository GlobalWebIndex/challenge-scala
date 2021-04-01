package pl.datart.csvtojson

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.unmarshalling._
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import pl.datart.csvtojson.model.JsonFormats._
import pl.datart.csvtojson.model._
import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._

import scala.io.Source


class MainIntegrationTest extends AsyncFunSpec with Matchers with Directives {

  private implicit val system: ActorSystem[Unit] = ActorSystem(Behaviors.empty, "SingleRequest")
  private val uuidRegex = "([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})"

  describe("main") {
    it("runs server HTTP server properly") {
      Main.main(Array())

      val rawUri = RawUri(uri = Source.getClass.getResource("/example_file.csv").getPath)

      val assertionF = Marshal(rawUri).to[RequestEntity].flatMap { entity =>
        val enqueueRequest = HttpRequest(
          method = HttpMethods.POST,
          uri = "http://localhost:8080/task",
          entity = entity
        )
        Http().singleRequest(enqueueRequest)
      }.flatMap { response =>
          Unmarshal(response).to[TaskId]
        }.map(_.taskId.matches(uuidRegex) shouldBe true)

      assertionF
        .onComplete(_ => system.terminate())

      assertionF
    }
  }
}
