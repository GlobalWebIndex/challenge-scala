package pool.dependencies

import akka.stream.scaladsl.Sink
import pool.interface.TaskFinishReason

trait Saver[CFG, ID, OUT, ITEM] {
  def make(file: OUT): Sink[ITEM, _]
  def unmake(file: OUT, reason: TaskFinishReason): Unit
  def target(config: CFG, taskId: ID): OUT
}
