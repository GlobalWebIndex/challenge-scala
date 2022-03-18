package gwi

import gwi.text_transform.StatefullTask

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

case class Task[State, Result](task: StatefullTask[State, Result], future: Cancellable[Result])

trait TasksList[State] {
  def getTaskState(taskId: UUID): Option[State]
}

class Tasks[State, Result](implicit executionContext: ExecutionContext) extends TasksList[State] {

  val tasks = new ConcurrentHashMap[UUID, Task[State, Result]]().asScala

  def addTask(id: UUID, task: StatefullTask[State, Result]): Unit = {
    val future = new Cancellable[Result](executionContext, task.doTask)
    tasks.addOne(id -> Task(task, future = future))
  }

  def cancelTask(id: UUID): Unit = {
    tasks.get(id).map(t => {
      println(s"canceling task $id" + t)

      // stop execution
      t.task.interruptTask()
      t.future.cancel()

      // remove the task and it's data
      tasks.remove(id)
    })
  }

  def isCanceled(id: UUID): Option[Boolean] = {
    tasks.get(id).map(t => {
      t.future.isCanceled()
    })
  }

  override def getTaskState(taskId: UUID): Option[State] = {
    tasks.get(taskId).map(_.task.getTaskState())
  }
}

object Tasks {
  def apply[State, Result]()(implicit executionContext: ExecutionContext): Tasks[State, Result] =
    new Tasks[State, Result]()
}
