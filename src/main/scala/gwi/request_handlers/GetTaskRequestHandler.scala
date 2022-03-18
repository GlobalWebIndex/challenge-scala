package gwi.request_handlers

import akka.NotUsed
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.stream.scaladsl.Source
import gwi.{AppContext, Status, TaskState, Tasks}

import java.util.UUID
import scala.concurrent.duration._

class GetTaskRequestHandler(appContext: AppContext) extends RequestHandler[String, Source[TaskState, NotUsed]] {

  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()

  override def handle(taskId: String): Source[TaskState, NotUsed] = {
    Source.cycle(()=> new TaskStateIter(appContext.tasks, taskId))
      .throttle(1, 2.seconds)
  }

  private class TaskStateIter(tasks:Tasks[TaskState, Unit], taskId: String) extends Iterator[TaskState] {
    var keepPolling = true

    override def hasNext: Boolean = {
      keepPolling
    }

    override def next(): TaskState = {
      val taskState = tasks
        .getTaskState(UUID.fromString(taskId))
        .getOrElse(throw NotFoundException())

      if(taskState.status.eq(Status.Done.toString) || taskState.status.eq(Status.Canceled.toString)) {
        keepPolling = false
      }

      taskState
    }
  }

}
