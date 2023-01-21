package conversion
import models.TaskId

trait Namer {
  def makeTaskId(): TaskId
}
object UUIDNamer extends Namer {
  def makeTaskId(): TaskId = TaskId(java.util.UUID.randomUUID.toString())
}
