package com.gwi.repository

import com.gwi.model.TaskDetail

import java.util.UUID
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

class InMemoryTaskRepository extends TaskRepository {
  val state = new TrieMap[UUID, TaskDetail]()

  override def upsertTask(task: TaskDetail): Future[UUID] = {
    state.addOne(task.id, task)
    Future.successful(task.id)
  }

  override def getTask(taskId: UUID): Future[Option[TaskDetail]] = Future.successful(state.get(taskId))

  override def getTaskIds: Future[List[UUID]] = Future.successful(state.keys.toList)
}
