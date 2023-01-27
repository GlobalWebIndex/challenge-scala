package pool

/** A running task */
trait Worker {

  /** Request to cancel the task
    *
    * @param onCancel
    *   Callback to acknowledge the finished task and return the number of
    *   processed items
    */
  def cancel(onCancel: Long => Unit): Unit

  /** Request to provide the number of processed items
    *
    * @param onCount
    *   Callback to return the number of processed items
    */
  def currentCount(onCount: Long => Unit): Unit
}

/** A factory, creating new workers */
trait WorkerFactory[IN, OUT] {

  /** Create a new worker
    *
    * @param taskId
    *   Task identifier
    * @param source
    *   Source
    * @param destination
    *   Destination
    * @param onDone
    *   Callback to report the task finishing normally
    * @param onFailure
    *   Callback to report the task failing
    * @return
    *   The newly created worker
    */
  def createWorker(
      source: IN,
      destination: OUT,
      onDone: Long => Unit,
      onFailure: Long => Unit
  ): Worker
}
