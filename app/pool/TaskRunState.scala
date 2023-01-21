package pool

import pool.Worker

sealed trait TaskRunState[IN, OUT]
object TaskRunState {
  final case class Scheduled[IN, OUT](url: IN, result: OUT)
      extends TaskRunState[IN, OUT]
  final case class Running[IN, OUT](
      runningSince: Long,
      worker: Worker,
      result: OUT,
      cancellationInProgress: Boolean
  ) extends TaskRunState[IN, OUT]
  final case class Cancelled[IN, OUT]() extends TaskRunState[IN, OUT]
  final case class Failed[IN, OUT]() extends TaskRunState[IN, OUT]
  final case class Done[IN, OUT](
      runningSince: Long,
      finishedAt: Long,
      linesProcessed: Long,
      result: OUT
  ) extends TaskRunState[IN, OUT]
}
