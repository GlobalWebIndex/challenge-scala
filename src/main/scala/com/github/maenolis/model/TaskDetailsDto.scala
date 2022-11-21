package com.github.maenolis.model

case class TaskDetailsDto(
    linesProcessed: Option[Long] = None,
    avgLinesProcessed: Option[Float] = None,
    state: String,
    downloadUri: String
)
