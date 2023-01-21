package controllers

import org.scalatest.wordspec.AnyWordSpec
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.model.StatusCodes
import org.scalatest.matchers.should.Matchers
import akka.http.scaladsl.model.ContentTypes

class CheckControllerSpec
    extends AnyWordSpec
    with Matchers
    with ScalatestRouteTest {
  val checkController = new CheckController()
  "CheckController GET" should {
    "report successful running" in {
      Get() ~> checkController.check ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`text/plain(UTF-8)`
        responseAs[String] shouldBe "running"
      }
    }
  }
}
