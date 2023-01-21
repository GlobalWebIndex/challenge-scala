package pool
import models.TaskId

trait Namer {
  def makeTaskId(): TaskId
}
