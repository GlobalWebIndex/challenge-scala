package gwi

import akka.actor.testkit.typed.scaladsl._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import java.nio.charset.StandardCharsets
import java.nio.file._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import scala.jdk.StreamConverters._

class StorageSpec extends ActorTestKitBase(ActorTestKit()) with AsyncFlatSpecLike with Matchers with BeforeAndAfterAll {
  val path: Path = Files.createTempDirectory("TaskServiceSpec")
  val storage: Storage = Storage(path)

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    Files.walk(path).toScala(LazyList).reverse.foreach(Files.delete)
  }

  "StorageSpec" should "sink source to a file" in {
    val source = Source.single(ByteString("sample", StandardCharsets.UTF_8))
    val f = source.runWith(storage.sink(TaskId(1)))
    f.map { _ =>
      Files.readString(path.resolve("1.json")) shouldBe "sample"
    }
  }

  // TODO: more Storage tests
}
