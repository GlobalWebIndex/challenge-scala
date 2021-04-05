package gwi

import akka.stream.IOResult
import akka.stream.scaladsl._
import akka.util.ByteString
import java.nio.file.Path
import scala.concurrent.Future

object Storage {
  def apply(path: Path): Storage = new StorageImpl(path)
}

trait Storage {
  def source(id: TaskId): Source[ByteString, Future[IOResult]]
  def sink(id: TaskId): Sink[ByteString, Future[IOResult]]
}

private class StorageImpl(path: Path) extends Storage {
  override def source(id: TaskId): Source[ByteString, Future[IOResult]] =
    FileIO.fromPath(resolvePath(id))

  override def sink(id: TaskId): Sink[ByteString, Future[IOResult]] =
    FileIO.toPath(resolvePath(id))

  private def resolvePath(id: TaskId): Path =
    path.resolve(s"${id.value}.json")
}
