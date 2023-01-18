package controllers

import org.scalatestplus.play.PlaySpec
import play.api.test.FakeRequest
import play.api.test.Helpers._

class CheckControllerSpec extends PlaySpec {
  "CheckController GET" should {
    "report successful running" in {
      val check = route(Mock.application, FakeRequest(GET, "/check")).get

      status(check) mustBe OK
      contentType(check) mustBe Some("text/plain")
      contentAsString(check) must be("running")
    }
  }
}
