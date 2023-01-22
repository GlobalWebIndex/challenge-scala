package pool.dependencies

import pool.interface.TaskFinishReason

import akka.stream.scaladsl.Sink

/** Customizable utilities for dealing with destinations */
trait Saver[ID, OUT, ITEM] {

  /** Creates a sink to write down the items
    *
    * @param file
    *   Destination address
    * @return
    *   Sink to consume the incoming items
    */
  def make(file: OUT): Sink[ITEM, _]

  /** Cleans up after the task has finished
    *
    * For example, it might remove the (incomplete) file in case the task was
    * finished abnormally
    *
    * @param file
    *   Destination address
    * @param reason
    *   Reason the task stopped
    */
  def unmake(file: OUT, reason: TaskFinishReason): Unit

  /** Creates an destination address from the given task identifier
    *
    * @param taskId
    *   Task identifier
    * @return
    *   Destination address
    */
  def target(taskId: ID): OUT
}
