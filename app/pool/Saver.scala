package pool

import akka.stream.scaladsl.Sink
import akka.util.ByteString

import java.nio.file.Path

trait Saver {
  def make(file: Path): Sink[ByteString, _]
  def unmake(file: Path): Unit
}
