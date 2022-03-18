package gwi


import gwi.io.{InputStreamProvider, OutputStreamProvider}
import gwi.text_transform.CsvToJsonTask
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers.equal
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.util.UUID
import scala.concurrent.ExecutionContext

/*
 to allow perform tests in with predictable execution order, we will extend ExecutionContext
 to ensure everything is running sync in the same thread
 */
class TestTasks extends AnyFunSuite with ExecutionContext {

  val inputCsv = "col-a ,col-b , col-c\n1a,1b,1c"
  val expectedJson = "{\"col-a\":\"1a\",\"col-b\":\"1b\",\"col-c\":\"1c\"}\n"

  implicit val executionContext = this

  val tasiId = UUID.fromString("1fe7c0f9-910b-40f7-899f-12d499324287")

  val inputStreamProvider = new DummyInputStreamProvider(inputCsv)
  val outputStreamProvider = new DummyOutputStreamProvider()

  val testAppContext = AppContext(
    tasks = Tasks[TaskState, Unit](),
    inputStreamProvider = inputStreamProvider,
    outputStreamProvider = outputStreamProvider,
    persistanceBaseUrl = s"dummy-url"
  )

  test("create and return task by id") {
    testAppContext
      .tasks
      .addTask(tasiId, new CsvToJsonTask("dummy-url", "dummy-file-name", testAppContext))
    val status = testAppContext.tasks.getTaskState(tasiId)

    assert(status.isDefined)
  }

  test("run task immediately if there is a free thread") {
    testAppContext
      .tasks
      .addTask(tasiId, new CsvToJsonTask("dummy-url", "dummy-file-name", testAppContext))
    val status = testAppContext.tasks.getTaskState(tasiId).getOrElse(assert(false))
    val result = outputStreamProvider.os.toString

    result should equal (expectedJson)
  }

  class DummyInputStreamProvider(csv: String) extends InputStreamProvider {
    override def getInputStream(uri: String): InputStream = {
      import java.io.ByteArrayInputStream
      new ByteArrayInputStream(csv.getBytes)
    }
  }

  class DummyOutputStreamProvider extends OutputStreamProvider {
    var os = new ByteArrayOutputStream()
    override def getOutputStream(fileName: String): OutputStream = {
      os = new ByteArrayOutputStream()
      os
    }
  }

  override def execute(runnable: Runnable): Unit = runnable.run

  override def reportFailure(t: Throwable): Unit = throw t

}
