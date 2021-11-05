package com.gwi.api

import com.gwi.Main.ServerUri
import com.gwi.execution.Task
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import java.time.Instant
import java.util.UUID

object TaskDetail {
  implicit val taskDetailResponseEncoder: Encoder[TaskDetail] = deriveEncoder

  def getLinesRate(task: Task): Long = task match {
    // Finished task
    case Task(_, _, Some(start), Some(end), linesProcessed, _) => linesProcessed / (end.getEpochSecond - start.getEpochSecond)
    // In progress task
    case Task(_, _, Some(start), None, linesProcessed, _) => linesProcessed / (Instant.now().getEpochSecond - start.getEpochSecond)
    case _ => 0
  }

  def getMaybeResultUri(task: Task): Option[String] = task.state match {
    case TaskState.Done => Some(s"$ServerUri/task/${task.id}/result")
    case _ => None
  }

  def fromTask(task: Task): TaskDetail = TaskDetail(task.id, task.linesProcessed, getLinesRate(task), task.state, getMaybeResultUri(task))
}

case class TaskDetail(
    id: UUID,
    linesProcessed: Long = 0,
    linesPerSecond: Long = 0,
    state: TaskState = TaskState.Scheduled,
    result: Option[String] = None
)
