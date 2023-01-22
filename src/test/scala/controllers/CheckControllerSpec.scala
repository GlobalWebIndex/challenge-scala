package controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest

class CheckControllerSpec
    extends AnyWordSpec
    with Matchers
    with ScalatestRouteTest {
  val log: Logger = LoggerFactory.getLogger("CheckControllerSpec")

  "CheckController" should {
    "report successful running" in {
      val controller = new CheckController(log)
      Get() ~> controller.check ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`text/plain(UTF-8)`
        responseAs[String] shouldBe "running"
      }
    }
  }
}
