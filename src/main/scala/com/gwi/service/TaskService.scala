package com.gwi.service

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.gwi.model.TaskDetail

import java.net.URI
import java.util.UUID
import scala.concurrent.Future

trait TaskService {

  def createTask(csvUrl: URI): Future[UUID]
  def getTask(taskId: UUID): Future[Option[TaskDetail]]
  def listTaskIds(): Future[List[UUID]]
  def cancelTask(taskId: UUID): Future[Either[String, TaskDetail]]

  def downloadJson(taskId: UUID): Option[Source[ByteString, _]]

}
