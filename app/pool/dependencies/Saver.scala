package pool.dependencies

import akka.stream.scaladsl.Sink

trait Saver[CFG, ID, OUT, ITEM] {
  def make(file: OUT): Sink[ITEM, _]
  def unmake(file: OUT): Unit
  def target(config: CFG, taskId: ID): OUT
}
