package com.gwi.service.dto

import java.util.UUID

case class TaskDto(id: UUID, linesProcessed: Long, averageLinesProccesed: Long, state: String, result: Option[String])
