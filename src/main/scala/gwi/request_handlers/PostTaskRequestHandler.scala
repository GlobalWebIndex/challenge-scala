package gwi.request_handlers

import gwi.AppContext
import gwi.text_transform.CsvToJsonTask

import java.util.UUID

class PostTaskRequestHandler(appContext: AppContext) extends RequestHandler[String, UUID]{
  override def handle(csvUrl: String): UUID = {
    val id = UUID.randomUUID()
    appContext.tasks.addTask(id, new CsvToJsonTask(csvUrl, s"data/$id.json", appContext))
    id
  }
}
