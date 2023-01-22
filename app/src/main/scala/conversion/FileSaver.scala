package conversion

import models.TaskId
import org.slf4j.Logger
import pool.dependencies.Saver
import pool.interface.TaskFinishReason

import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.util.ByteString

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class FileSaver(log: Logger, resultDirectory: Path)
    extends Saver[TaskId, Path, ByteString] {
  def make(file: Path): Sink[ByteString, _] =
    Flow[ByteString]
      .map(ByteString("  ").concat(_))
      .intersperse(ByteString("[\n"), ByteString(",\n"), ByteString("\n]"))
      .to(FileIO.toPath(file))
  def unmake(file: Path, reason: TaskFinishReason): Unit =
    if (reason != TaskFinishReason.Done)
      try {
        Files.deleteIfExists(file)
      } catch {
        case e: IOException => log.error(s"Error deleting the file $file: $e")
      }
  def target(taskId: TaskId): Path =
    resultDirectory.resolve(s"${taskId.id}.json")
}
