package pool

import pool.interface.TaskFinishReason

sealed trait TaskRunState[IN, OUT]
object TaskRunState {
  final case class Scheduled[IN, OUT](url: IN, result: OUT)
      extends TaskRunState[IN, OUT]
  final case class Running[IN, OUT](
      runningSince: Long,
      worker: Worker,
      result: OUT,
      countingRequests: Vector[Long => Unit]
  ) extends TaskRunState[IN, OUT]
  final case class Finished[IN, OUT](
      runningSince: Long,
      finishedAt: Long,
      linesProcessed: Long,
      result: OUT,
      reason: TaskFinishReason
  ) extends TaskRunState[IN, OUT]
}
