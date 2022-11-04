package com.gwi.database.model.memory.dao

import com.google.inject.Singleton
import com.gwi.database.model.memory.Task

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable

@Singleton
class TaskRepository {
  private val taskMap: AtomicReference[mutable.Map[UUID, Task]] = new AtomicReference(mutable.Map[UUID, Task]())

  def upsert(task: Task): Unit = {
    taskMap.getAndUpdate(map => {
      map.update(task.id, task)
      map
    })
  }

  def addBulk(tasks: List[Task]): Unit = tasks.foreach(task => upsert(task))

  def get(taskId: UUID): Option[Task] = taskMap.get.get(taskId)

  def getAll(): List[Task] = taskMap.get().values.toList

  // Only for testing purposes
  def deleteAll(): Unit = taskMap.getAndUpdate(_ => mutable.Map[UUID, Task]())
}
