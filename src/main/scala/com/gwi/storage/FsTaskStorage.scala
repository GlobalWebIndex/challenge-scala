package com.gwi.storage

import akka.Done
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

import java.nio.file.{Files, Paths}
import java.util.UUID

class FsTaskStorage(rootDirectoryPath: String) extends TaskStorage {
  Files.createDirectories(Paths.get(rootDirectoryPath))

  override def taskJsonSource(taskId: UUID): Option[Source[ByteString, _]] = {
    val path = Paths.get(s"$rootDirectoryPath/${taskId.toString}.json")
    val file = path.toFile
    if (file.exists() && file.canRead) {
      Some(FileIO.fromPath(path))
    } else None
  }

  override def addFile(): Done = ???
}
