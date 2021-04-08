package pl.datart.csvtojson.http

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.testkit._
import better.files.File
import cats.effect.IO
import org.scalatest.funspec._
import org.scalatest.matchers.should._
import pl.datart.csvtojson.model._
import pl.datart.csvtojson.service._
import pl.datart.csvtojson.util.FAdapter.FAdapterIOFGlobal._

import scala.jdk.CollectionConverters._
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

class HttpRoutesTest extends AsyncFunSpec with Matchers with ScalatestRouteTest {

  private val mockedTaskScheduler = new TaskScheduler[IO] {
    override def schedule(rawUri: RawUri): IO[TaskId]                       = IO.pure(TaskId(""))
    override def cancelTask(taskId: TaskId): IO[Option[CancellationResult]] = IO.pure(Option.empty[CancellationResult])
  }

  private val mockedTaskService = new TaskService[IO] {
    override def getTasks: IO[Iterable[TaskId]]            = IO.pure(Iterable.empty[TaskId])
    override def getTask(taskId: TaskId): IO[Option[Task]] = IO.pure(Option.empty[Task])
  }

  describe("routes") {
    it("should return result file if it exists") {
      val fileContent = s"""[{"a":1,"b":2},{"a":3,"b":4}]"""
      val uuid        = UUID.randomUUID()
      val testFile    = File(Paths.get(System.getProperty("java.io.tmpdir"))) / s"${uuid.toString}.json"
      testFile.createFile().appendLine(fileContent)

      val testedImplementation: HttpRoutes[IO] = new HttpRoutes(mockedTaskScheduler, mockedTaskService)(adapter)
      Get(s"/file/${uuid.toString}") ~> testedImplementation.routes ~> check {
        responseAs[String] shouldBe fileContent
      }
    }

    it("should fail to return result file if it's not readable") {
      val uuid = UUID.randomUUID()
      val _    = (File(Paths.get(System.getProperty("java.io.tmpdir"))) / s"${uuid.toString}.json")
        .createFile()
        .setPermissions(PosixFilePermissions.fromString("-wx-wx---").asScala.toSet)

      val testedImplementation: HttpRoutes[IO] = new HttpRoutes(mockedTaskScheduler, mockedTaskService)(adapter)
      Get(s"/file/${uuid.toString}") ~> testedImplementation.routes ~> check {
        response.status shouldBe InternalServerError
      }
    }

    it("should not return result file if it doesn't exist") {
      val uuid = UUID.randomUUID()

      val testedImplementation: HttpRoutes[IO] = new HttpRoutes(mockedTaskScheduler, mockedTaskService)(adapter)
      Get(s"/file/${uuid.toString}") ~> testedImplementation.routes ~> check {
        response.status shouldBe NotFound
      }
    }

    it("should return not found for non-existing task cancellation") {
      val uuid = UUID.randomUUID()

      val testedImplementation: HttpRoutes[IO] = new HttpRoutes(mockedTaskScheduler, mockedTaskService)(adapter)
      Delete(s"/task/${uuid.toString}") ~> testedImplementation.routes ~> check {
        response.status shouldBe NotFound
      }
    }
  }
}
