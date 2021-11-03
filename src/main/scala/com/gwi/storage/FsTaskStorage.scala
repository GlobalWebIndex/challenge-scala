package com.gwi.storage

import akka.Done
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.util.UUID

class FsTaskStorage(rootDirectoryPath: String) extends TaskStorage {
  Files.createDirectories(Paths.get(rootDirectoryPath))

  override def taskJsonSource(taskId: UUID): Option[Source[ByteString, _]] = {
    val path = Paths.get(s"$rootDirectoryPath/${taskId.toString}.json")
    read(path)
  }

  override def addFile(): Done = ???

  override def csvSource(filePath: URI): Option[Source[ByteString, _]] = {
    val path = Paths.get(filePath)
    read(path)
  }

  private def read(path: Path) = {
    val file = path.toFile
    if (file.exists() && file.canRead) {
      Some(FileIO.fromPath(path))
    } else None
  }
}
