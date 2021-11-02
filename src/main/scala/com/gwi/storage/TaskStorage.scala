package com.gwi.storage

import akka.Done
import akka.stream.scaladsl.Source
import akka.util.ByteString

import java.util.UUID

trait TaskStorage {

  def taskJsonSource(taskId: UUID): Option[Source[ByteString, _]]
  def addFile(): Done

}
