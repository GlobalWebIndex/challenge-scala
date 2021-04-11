package pl.datart.csvtojson.model

import akka.http.scaladsl.model.Uri

import java.util.Date

final case class Task(
    taskId: TaskId,
    uri: Uri,
    state: TaskState,
    startTime: Option[Date],
    endTime: Option[Date]
) {
  def isInTerminal: Boolean = {
    Set[TaskState](TaskState.Canceled, TaskState.Failed, TaskState.Done).contains(state)
  }
  def isInProgress: Boolean = {
    Set[TaskState](TaskState.Scheduled, TaskState.Running).contains(state)
  }
  def isCanceledOrFailed: Boolean = {
    Set[TaskState](TaskState.Canceled, TaskState.Failed).contains(state)
  }
}
