package gwi

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ExceptionHandlerSpec extends AnyFlatSpec with Matchers with ScalatestRouteTest {
  def seal(route: Route): Route = handleExceptions(ExceptionHandler())(route)

  "ExceptionHandler" should "catch BadRequestErrors" in {
    val route = complete {
      BadRequestError("error message")
    }

    Get() ~> seal(route) ~> check {
      handled shouldBe true
      status shouldBe BadRequest
      responseAs[String] shouldBe "error message"
    }
  }
}
