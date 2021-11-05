package com.gwi.storage

import akka.stream.IOResult
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import java.util.UUID
import scala.concurrent.Future

trait TaskStorage {
  def jsonSource(taskId: UUID): Option[Source[ByteString, _]]
  def jsonSink(taskId: UUID): Sink[ByteString, Future[IOResult]]
}
