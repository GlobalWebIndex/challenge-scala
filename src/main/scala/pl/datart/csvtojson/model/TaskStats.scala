package pl.datart.csvtojson.model

final case class TaskStats(
    linesProcessed: Long,
    avgLinesPerSec: Long,
    state: TaskState,
    result: Option[String]
)
