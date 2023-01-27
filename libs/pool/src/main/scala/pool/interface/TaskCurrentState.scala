package pool.interface

/** Current state of a task */
sealed trait TaskCurrentState[OUT]

/** All possible states of a task */
object TaskCurrentState {

  /** Task hasn't started yet */
  final case class Scheduled[OUT]() extends TaskCurrentState[OUT]

  /** Task is currently running */
  final case class Running[OUT]() extends TaskCurrentState[OUT]

  /** Task is finished
    *
    * @param at
    *   Timestamp (in milliseconds) of the time the task stopped
    * @param destination
    *   Task destination
    * @param reason
    *   The reason why the task stopped
    */
  final case class Finished[OUT](
      at: Long,
      destination: OUT,
      reason: TaskFinishReason
  ) extends TaskCurrentState[OUT]
}
