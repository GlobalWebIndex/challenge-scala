package com.github.maenolis.model

object TaskStatus extends Enumeration {
  type TaskStatusEnum = Value
  val Scheduled = Value("Scheduled")
  val Running = Value("Running")
  val Done = Value("Done")
  val Failed = Value("Failed")
  val Canceled = Value("Canceled")

  def isCancelable(status: TaskStatusEnum): Boolean = {
    status == Scheduled ||
    status == Running
  }

  def isTerminal(status: TaskStatusEnum): Boolean = {
    status == Done ||
    status == Failed ||
    status == Canceled
  }
}
