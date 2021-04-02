package pl.datart.csvtojson.model

import akka.actor.Cancellable
import akka.http.scaladsl.model.Uri

import java.util.Date

final case class Task(
    taskId: TaskId,
    uri: Uri,
    state: TaskState,
    cancelable: Option[Cancellable],
    scheduleTime: Date,
    startTime: Option[Date],
    endTime: Option[Date]
)
