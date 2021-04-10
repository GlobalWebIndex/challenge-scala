package pl.datart.csvtojson.model

import akka.http.scaladsl.model.Uri
import pl.datart.csvtojson.util.Cancellable

import java.util.Date

final case class Task(
    taskId: TaskId,
    uri: Uri,
    state: TaskState,
    cancelable: Option[Cancellable[Any]],
    scheduleTime: Date,
    startTime: Option[Date],
    endTime: Option[Date]
) {
  def isInTerminal: Boolean = {
    Set[TaskState](TaskState.Canceled, TaskState.Failed, TaskState.Done).contains(state)
  }
}
