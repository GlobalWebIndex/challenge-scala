package pool

import akka.http.scaladsl.model.Uri
import pool.Worker

import java.nio.file.Path
sealed trait TaskRunState
object TaskRunState {
  final case class Scheduled(url: Uri, result: Path) extends TaskRunState
  final case class Running(
      runningSince: Long,
      worker: Worker,
      result: Path,
      cancellationInProgress: Boolean
  ) extends TaskRunState
  object Cancelled extends TaskRunState
  object Failed extends TaskRunState
  final case class Done(
      runningSince: Long,
      finishedAt: Long,
      linesProcessed: Long,
      result: Path
  ) extends TaskRunState
}
