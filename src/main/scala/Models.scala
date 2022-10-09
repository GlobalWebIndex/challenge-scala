import cats.effect.IO
import fs2.concurrent.SignallingRef
import org.http4s._
import org.http4s.circe._
import io.circe.generic.auto._

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

object TaskStatus extends Enumeration {
  type Status = Value
  val SCHEDULED, RUNNING, DONE, FAILED, CANCELED = Value
}

object TaskModel {
  import TaskStatus._
  case class Task(id: String,
                  url: String,
                  status: AtomicReference[Status],
                  linesProcessed: AtomicInteger,
                  linesPerSecond: AtomicInteger,
                  avgLinesProcess: AtomicInteger,
                  cancel: SignallingRef[IO, Boolean]
                 )


  case class TaskInput(url: String)

  case class TaskOutput(id: String,
                        url: String,
                        status: String,
                        linesProcessed: Int,
                        avgLinesProcessed: Int)


  implicit class SerializeTask(task: Task) {
    def toOutput: TaskOutput = TaskOutput(task.id, task.url, task.status.get().toString,
      task.linesProcessed.get(), task.avgLinesProcess.get())
  }

  implicit val taskInputJsonDecoder: EntityDecoder[IO, TaskInput] = jsonOf[IO, TaskInput]

}
