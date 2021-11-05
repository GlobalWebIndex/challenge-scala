package com.gwi.service

import akka.Done
import akka.actor.Cancellable
import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.gwi.api.TaskDetail

import java.util.UUID
import scala.concurrent.Future

trait TaskService {

  def createTask(csvUri: Uri): Future[UUID]
  def getTask(taskId: UUID): Future[Option[TaskDetail]]
  def getTaskSource(taskId: UUID): Source[Option[TaskDetail], Cancellable]
  def listTaskIds(): Future[List[UUID]]
  def cancelTask(taskId: UUID): Future[Either[String, UUID]]

  def downloadJson(taskId: UUID): Option[Source[ByteString, _]]

}
