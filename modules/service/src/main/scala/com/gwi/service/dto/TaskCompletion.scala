package com.gwi.service.dto

import java.util.UUID

case class TaskCompletion(taskId: UUID, wasCanceled: Boolean, hasFailed: Boolean)
