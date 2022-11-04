package com.gwi.service.dto

object TaskCanceledResult extends Enumeration {
  type TaskCanceledResult = Value
  val SUCCESS: TaskCanceledResult = Value("Success")
  val NOT_FOUND: TaskCanceledResult = Value("Not Found")
  val NOT_CANCELABLE_STATE: TaskCanceledResult = Value("Not Cancelable State")
}
