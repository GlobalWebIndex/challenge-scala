package com.gwi.database.model.persistent

import java.time.LocalDateTime
import java.util.UUID

case class JsonLine(
  taskId: UUID,
  processTime: Long,
  line: String,
  timeCreated: LocalDateTime = LocalDateTime.now(),
  isComplete: Boolean = false,
  id: Option[Long] = None
)
