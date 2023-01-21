package conversion
import models.TaskId
import pool.Namer

object UUIDNamer extends Namer {
  def makeTaskId(): TaskId = TaskId(java.util.UUID.randomUUID.toString())
}
