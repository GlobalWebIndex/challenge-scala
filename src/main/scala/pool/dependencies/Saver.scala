package pool.dependencies

import pool.interface.TaskFinishReason

import akka.stream.scaladsl.Sink

trait Saver[ID, OUT, ITEM] {
  def make(file: OUT): Sink[ITEM, _]
  def unmake(file: OUT, reason: TaskFinishReason): Unit
  def target(taskId: ID): OUT
}
