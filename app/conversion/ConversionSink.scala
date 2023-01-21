package conversion

import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.util.ByteString

import java.nio.file.Files
import java.nio.file.Path

trait ConversionSink {
  def make(file: Path): Sink[ByteString, _]
  def unmake(file: Path): Unit
}
object FileConversionSink extends ConversionSink {
  def make(file: Path): Sink[ByteString, _] = FileIO.toPath(file)
  def unmake(file: Path): Unit = Files.deleteIfExists(file)
}
