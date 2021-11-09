package gwi.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.gwi.api.{JsonCodecs, TaskCreateRequest, TaskCreateResponse, TaskRouter}
import com.gwi.execution.{Task, TaskExecutor}
import com.gwi.repository.InMemoryTaskRepository
import com.gwi.service.TaskServiceImpl
import com.gwi.storage.TaskStorage
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax.EncoderOps
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should

import scala.concurrent.{ExecutionContextExecutor, Future}

class TaskRouterSpec
    extends AsyncFlatSpec
    with should.Matchers
    with AsyncMockFactory
    with ScalatestRouteTest
    with FailFastCirceSupport
    with JsonCodecs {

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  "TaskRouter" should "handle create task functionality properly" in {
    val repo = new InMemoryTaskRepository()
    val storage = mock[TaskStorage]
    val executor = mock[TaskExecutor]

    val taskService = new TaskServiceImpl(repo, storage, executor)
    val taskRouter = new TaskRouter(taskService)
    val uri = "https://www.sample.com/source.csv"

    (executor.enqueueTask _).expects(*).returns(Future.successful(true))

    val request = TaskCreateRequest(uri)
    Post("/task", request.asJson) ~> taskRouter.routes ~> check {
      status shouldBe StatusCodes.Accepted
      val createdTaskId = entityAs[TaskCreateResponse].taskId

      repo.getTask(createdTaskId).map(_ shouldEqual Some(Task(createdTaskId, uri)))
    }
  }

  // TODO add more tests

}
