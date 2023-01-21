package conversion

import conversion.ConversionConfig
import models.TaskId
import pool.dependencies.Saver

import akka.stream.scaladsl.{FileIO, Flow, Sink}
import akka.util.ByteString

import java.nio.file.{Files, Path}

object FileSaver extends Saver[ConversionConfig, TaskId, Path, ByteString] {
  def make(file: Path): Sink[ByteString, _] =
    Flow[ByteString]
      .map(ByteString("  ").concat(_))
      .intersperse(ByteString("[\n"), ByteString(",\n"), ByteString("]"))
      .to(FileIO.toPath(file))
  def unmake(file: Path): Unit = Files.deleteIfExists(file)
  def target(config: ConversionConfig, taskId: TaskId): Path =
    config.resultDirectory.resolve(s"${taskId.id}.json")
}
