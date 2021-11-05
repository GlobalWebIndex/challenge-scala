package com.gwi.storage

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString

import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class FsTaskStorage(rootDirectoryPath: String)(implicit ec: ExecutionContext) extends TaskStorage {

  Files.createDirectories(Paths.get(rootDirectoryPath))

  def getJsonPath(rootDirectoryPath: String, taskId: UUID): Path =
    Paths.get(s"$rootDirectoryPath/${taskId.toString}.json")

  override def jsonSource(taskId: UUID): Option[Source[ByteString, _]] = {
    val path = getJsonPath(rootDirectoryPath, taskId)
    read(path)
  }

  override def jsonSink(taskId: UUID): Sink[ByteString, Future[IOResult]] = {
    val path = getJsonPath(rootDirectoryPath, taskId)
    write(path)
  }

  private def read(path: Path) = {
    if (Files.exists(path) && Files.isReadable(path)) {
      Some(FileIO.fromPath(path))
    } else None
  }

  private def write(path: Path) = {
    if (Files.notExists(path)) { FileIO.toPath(path) }
    else Sink.ignore.mapMaterializedValue(_ => Future.failed[IOResult](new Exception("File already exists!")))
  }
}
