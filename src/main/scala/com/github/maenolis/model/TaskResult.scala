package com.github.maenolis.model

import spray.json.JsValue

final case class TaskResult(id: Long, csvContent: JsValue, taskId: Long)
