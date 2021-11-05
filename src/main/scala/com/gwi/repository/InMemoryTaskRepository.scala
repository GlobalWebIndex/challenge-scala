package com.gwi.repository

import com.gwi.execution.Task

import java.util.UUID
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.Future

object InMemoryTaskRepository {
  def createSet[T](): mutable.Set[T] = {
    import scala.jdk.CollectionConverters._
    java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap[T, java.lang.Boolean]).asScala
  }
}

class InMemoryTaskRepository extends TaskRepository {
  private val state = new TrieMap[UUID, Task]()

  override def insertTask(task: Task): Future[UUID] = {
    state.addOne(task.id, task)
    Future.successful(task.id)
  }

  def updateTask(task: Task): Future[UUID] = {
    state.update(task.id, task)
    Future.successful(task.id)
  }

  override def getTask(taskId: UUID): Future[Option[Task]] = Future.successful(state.get(taskId))

  override def getTaskIds: Future[List[UUID]] = Future.successful(state.keys.toList)

  def setLinesProcessed(taskId: UUID, linesProcessed: Long): Future[Long] = {
    state
      .get(taskId)
      .map { t =>
        state.update(t.id, t.copy(linesProcessed = linesProcessed))
        Future.successful(linesProcessed)
      }
      .getOrElse(Future.failed(new Exception("Unexpected state - task does not exists in state")))
  }
}
