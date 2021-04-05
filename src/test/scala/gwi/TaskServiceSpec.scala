package gwi

import akka.actor.testkit.typed.scaladsl._
import akka.http.scaladsl.model.Uri
import akka.pattern.StatusReply._
import gwi.Manager._
import java.nio.file._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import scala.jdk.StreamConverters._

class TaskServiceSpec
    extends ActorTestKitBase(ActorTestKit())
    with AsyncFlatSpecLike
    with Matchers
    with BeforeAndAfterAll {

  val path: Path = Files.createTempDirectory("TaskServiceSpec")
  val storage: Storage = Storage(path)

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    Files.walk(path).toScala(LazyList).reverse.foreach(Files.delete)
  }

  "TaskService" should "handle a create task request" in {
    val probe = testKit.createTestProbe[Command]()
    val service = TaskService(probe.ref, storage)

    val source = Uri("/source.csv")
    val id = TaskId(1)

    val result = service.create(source)

    val msg = probe.expectMessageType[Create]
    msg.source shouldBe source

    msg.replyTo ! success(id)

    result.map {
      _ shouldBe id
    }
  }

  // TODO: more TaskService tests
}
