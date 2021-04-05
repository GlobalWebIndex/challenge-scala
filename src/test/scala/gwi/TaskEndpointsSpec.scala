package gwi

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe.Json
import org.scalamock.scalatest.MockFactory
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.Future

class TaskEndpointsSpec
    extends AnyFlatSpec
    with Matchers
    with MockFactory
    with ScalatestRouteTest
    with TaskJsonFormats {

  "TaskEndpoints" should "handle a create task request" in {
    val service = stub[TaskService]
    (service.create _).when(Uri("/source.csv")).returns(Future.successful(TaskId(1)))
    (service.create _).when(*).returns(Future.successful(TaskId(2)))
    withRoute(service) { route =>
      val request = Json.obj("source" -> Json.fromString(value = "/source.csv"))
      Post("/task", request) ~> route ~> check {
        handled shouldBe true
        status shouldBe StatusCodes.OK
        entityAs[Json] shouldBe Json.obj("id" -> Json.fromInt(value = 1))
      }
    }
  }

  // TODO: more TaskEndpoints tests

  def withRoute(service: TaskService)(f: Route => Assertion): Assertion =
    f(new TaskEndpoints(service).route)
}
