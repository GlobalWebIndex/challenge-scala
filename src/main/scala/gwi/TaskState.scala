package gwi


import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

object Status extends Enumeration {
  type TaskStatus = Value

  val Scheduled, Running, Done, Failed, Canceled = Value
}

case class TaskState(status: String, avgLinesProcessed: Double, resultUrl: Option[String] = None) {
  def isCanceled():Boolean = this.synchronized {
    status == Status.Canceled
  }
}


object TaskState extends SprayJsonSupport with DefaultJsonProtocol {
  val initial = TaskState(status = Status.Scheduled.toString, avgLinesProcessed = 0, resultUrl = None)

  implicit val taskStateFormat = jsonFormat3(TaskState.apply)
}
