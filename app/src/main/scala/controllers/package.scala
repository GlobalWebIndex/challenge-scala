import models.TaskDetails
import models.TaskId
import models.TaskShortDetails
import pool.interface.TaskCurrentState
import pool.interface.TaskFinishReason
import pool.interface.TaskInfo
import pool.interface.TaskShortInfo
import pool.interface.TaskState

package object controllers {
  def taskShortInfoToDetails(
      info: TaskShortInfo[TaskId],
      resultUrl: TaskId => String
  ): (String, TaskShortDetails) = {
    val result = info.state match {
      case TaskState.DONE => Some(resultUrl(info.taskId))
      case _              => None
    }
    (
      info.taskId.id,
      TaskShortDetails(info.state, result)
    )
  }
  def taskInfoToDetails(
      info: TaskInfo[TaskId, _],
      resultUrl: TaskId => String
  ): TaskDetails = {
    val lastTime = info.state match {
      case TaskCurrentState.Finished(at, _, _) => at
      case _                                   => System.currentTimeMillis
    }
    val runningTime = lastTime - info.runningSince
    val avgLinesProcessed =
      if (runningTime <= 0) 0 else info.linesProcessed * 1000.0 / runningTime
    val result = info.state match {
      case TaskCurrentState.Finished(_, _, TaskFinishReason.Done) =>
        Some(resultUrl(info.taskId))
      case _ => None
    }
    val state = info.state match {
      case TaskCurrentState.Scheduled() => TaskState.SCHEDULED
      case TaskCurrentState.Running()   => TaskState.RUNNING
      case TaskCurrentState.Finished(_, _, reason) =>
        reason match {
          case TaskFinishReason.Done      => TaskState.DONE
          case TaskFinishReason.Failed    => TaskState.FAILED
          case TaskFinishReason.Cancelled => TaskState.CANCELLED

        }
    }
    TaskDetails(info.linesProcessed, avgLinesProcessed, state, result)
  }
}
