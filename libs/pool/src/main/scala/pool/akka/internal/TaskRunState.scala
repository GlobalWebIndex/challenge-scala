package pool.akka.internal

import pool.Worker
import pool.interface.TaskFinishReason

sealed trait TaskRunState[OUT]
object TaskRunState {
  final case class Scheduled[OUT](destination: OUT) extends TaskRunState[OUT]
  final case class Running[OUT](
      runningSince: Long,
      worker: Worker,
      destination: OUT,
      countingRequests: Vector[Long => Unit]
  ) extends TaskRunState[OUT]
  final case class Finished[OUT](
      runningSince: Long,
      finishedAt: Long,
      linesProcessed: Long,
      destination: OUT,
      reason: TaskFinishReason
  ) extends TaskRunState[OUT]
}
