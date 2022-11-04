package com.gwi.database.model.memory

import com.gwi.database.model.memory.TaskState.TaskState

import java.util.UUID

object TaskState extends Enumeration {
  type TaskState = Value
  val SCHEDULED: TaskState = Value("Scheduled")
  val RUNNING: TaskState = Value("Running")
  val DONE: TaskState = Value("Done")
  val FAILED: TaskState = Value("Failed")
  val CANCELED: TaskState = Value("Canceled")
}

case class Task(id: UUID, linesProcessed: Long, totalProcessingTime: Long, state: TaskState, result: Option[String])
