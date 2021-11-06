package com.gwi.execution

import java.util.UUID
import scala.concurrent.Future

trait TaskExecutor {
  def cancelTaskExecution(taskId: UUID): Boolean
  def enqueueTask(taskId: UUID): Future[Boolean]
}
