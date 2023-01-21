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

object FileSaver extends Saver[ConversionConfig, TaskId, Path, ByteString] {
  def make(file: Path): Sink[ByteString, _] =
    Flow[ByteString]
      .map(ByteString("  ").concat(_))
      .intersperse(ByteString("[\n"), ByteString(",\n"), ByteString("]"))
      .to(FileIO.toPath(file))
  def unmake(file: Path, reason: TaskFinishReason): Unit =
    if (reason != TaskFinishReason.Done) Files.deleteIfExists(file)
  def target(config: ConversionConfig, taskId: TaskId): Path =
    config.resultDirectory.resolve(s"${taskId.id}.json")
}
