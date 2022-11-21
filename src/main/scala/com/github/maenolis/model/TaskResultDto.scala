package com.github.maenolis.model

import spray.json.JsValue

final case class TaskResultDto(csvContent: JsValue, taskId: Long)
