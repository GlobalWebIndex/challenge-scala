package pool.dependencies

import pool.interface.TaskFinishReason

trait Destination[OUT] {
  def sink(): OUT
  def finalize(reason: TaskFinishReason): Unit
}

trait Saver[ID, OUT] {
  def make(taskId: ID): OUT
}
