package com.gwi.storage

import akka.Done
import akka.stream.scaladsl.Source
import akka.util.ByteString

import java.net.URI
import java.util.UUID

trait TaskStorage {
  def csvSource(filePath: URI): Option[Source[ByteString, _]]
  def taskJsonSource(taskId: UUID): Option[Source[ByteString, _]]
  def addFile(): Done
}
