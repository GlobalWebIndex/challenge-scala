package com.gwi.api

import com.gwi.Main.ServerUri
import com.gwi.execution.Task

import java.time.Instant

object TaskTransformations {

  // This should not happen but returning -1 is seconds is 0
  private def calculateRate(lines: Long, seconds: Long) = {
    if (seconds != 0) lines / seconds
    else -1
  }

  def getLinesRate(task: Task): Long = task match {
    // Finished task
    case Task(_, _, Some(start), Some(end), linesProcessed, _) =>
      calculateRate(linesProcessed, end.getEpochSecond - start.getEpochSecond)
    // In progress task
    case Task(_, _, Some(start), None, linesProcessed, _) =>
      calculateRate(linesProcessed, Instant.now().getEpochSecond - start.getEpochSecond)
    case _ => 0
  }

  def getMaybeResultUri(task: Task): Option[String] = task.state match {
    case TaskState.Done => Some(s"$ServerUri/task/${task.id}/result")
    case _ => None
  }

  def toTaskDetail(task: Task): TaskDetail =
    TaskDetail(task.id, task.linesProcessed, getLinesRate(task), task.state, getMaybeResultUri(task))
}
