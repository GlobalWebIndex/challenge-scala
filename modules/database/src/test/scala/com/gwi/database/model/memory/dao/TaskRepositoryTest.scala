package com.gwi.database.model.memory.dao

import com.gwi.database.model.memory.dao.TaskRepository
import com.gwi.database.model.memory.{Task, TaskState}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class TaskRepositoryTest extends AnyWordSpec {

  "task repository" should {
    "perform read, write, update operations" in {
      val taskRepository = new TaskRepository()
      val taskId = UUID.randomUUID()
      val task = Task(taskId, 0L, 0L, TaskState.SCHEDULED)
      assert(taskRepository.get(taskId).isEmpty)
      taskRepository.upsert(task)
      assert(taskRepository.get(taskId).contains(task))

      val updatedTask = task.copy(state = TaskState.RUNNING)
      taskRepository.upsert(updatedTask)
      assert(taskRepository.get(taskId).contains(updatedTask))

      val newTaskId = UUID.randomUUID()
      val newTask = Task(newTaskId, 0L, 0L, TaskState.SCHEDULED)
      taskRepository.addBulk(List(newTask))
      assert(taskRepository.get(taskId).contains(updatedTask))
      assert(taskRepository.get(newTaskId).contains(newTask))
    }
  }
}
