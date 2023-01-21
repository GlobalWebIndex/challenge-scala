import models.TaskCurrentState
import models.TaskDetails
import models.TaskId
import models.TaskInfo
import models.TaskShortDetails
import models.TaskShortInfo
import models.TaskState
import play.api.mvc.RequestHeader

package object controllers {
  def taskShortInfoToDetails(info: TaskShortInfo)(implicit
      requestHeader: RequestHeader
  ): (TaskId, TaskShortDetails) = {
    val resultUrl = info.state match {
      case TaskState.DONE =>
        Some(
          routes.CsvToJsonController
            .taskResult(info.taskId)
            .absoluteURL(requestHeader.secure)
        )
      case _ => None
    }
    (
      info.taskId,
      TaskShortDetails(info.state, resultUrl)
    )
  }
  def taskInfoToDetails(
      info: TaskInfo
  )(implicit requestHeader: RequestHeader): TaskDetails = {
    val lastTime = info.state match {
      case TaskCurrentState.Done(at, _) => at
      case _                            => System.currentTimeMillis
    }
    val runningTime = lastTime - info.runningSince
    val avgLinesProcessed =
      if (runningTime <= 0) 0 else info.linesProcessed * 1000.0 / runningTime
    val resultUrl = info.state match {
      case TaskCurrentState.Done(_, _) =>
        Some(
          routes.CsvToJsonController
            .taskResult(info.taskId)
            .absoluteURL(requestHeader.secure)
        )
      case _ => None
    }
    val state = info.state match {
      case TaskCurrentState.Scheduled  => TaskState.SCHEDULED
      case TaskCurrentState.Running    => TaskState.RUNNING
      case TaskCurrentState.Done(_, _) => TaskState.DONE
      case TaskCurrentState.Failed     => TaskState.FAILED
      case TaskCurrentState.Cancelled  => TaskState.CANCELLED
    }
    TaskDetails(info.linesProcessed, avgLinesProcessed, state, resultUrl)
  }
}
