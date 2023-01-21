package conversion
import models.TaskId
import pool.dependencies.Namer

object UUIDNamer extends Namer[TaskId] {
  def makeTaskId(): TaskId = TaskId(java.util.UUID.randomUUID.toString())
}
