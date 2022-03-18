package gwi.request_handlers

import akka.NotUsed
import akka.stream.scaladsl.Source
import gwi.AppContext

import java.util.UUID

class DeleteTaskRequestHandler(appContext: AppContext) extends RequestHandler[String, Source[Unit, NotUsed]] {

  override def handle(taskId: String): Source[Unit, NotUsed] = {
    appContext.tasks.cancelTask(UUID.fromString(taskId))
    Source.single()
  }

}
