package conversion

import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import pool.Saver

import java.nio.file.Files
import java.nio.file.Path
object FileSaver extends Saver {
  def make(file: Path): Sink[ByteString, _] = FileIO.toPath(file)
  def unmake(file: Path): Unit = Files.deleteIfExists(file)
}
