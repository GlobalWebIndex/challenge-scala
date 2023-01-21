package conversion

import models.TaskId
import pool.dependencies.Saver
import pool.interface.TaskFinishReason

import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.util.ByteString

import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.Logger
import java.io.IOException

class FileSaver(log: Logger)
    extends Saver[ConversionConfig, TaskId, Path, ByteString] {
  def make(file: Path): Sink[ByteString, _] =
    Flow[ByteString]
      .map(ByteString("  ").concat(_))
      .intersperse(ByteString("[\n"), ByteString(",\n"), ByteString("]"))
      .to(FileIO.toPath(file))
  def unmake(file: Path, reason: TaskFinishReason): Unit =
    if (reason != TaskFinishReason.Done)
      try {
        Files.deleteIfExists(file)
      } catch {
        case e: IOException => log.error(s"Error deleting the file $file: $e")
      }
  def target(config: ConversionConfig, taskId: TaskId): Path =
    config.resultDirectory.resolve(s"${taskId.id}.json")
}
