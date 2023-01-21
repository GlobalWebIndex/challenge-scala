package conversion

import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import models.ConversionMessage
import models.TaskId
import models.TaskInfo
import models.TaskShortInfo

import scala.concurrent.Future
import akka.http.scaladsl.model.Uri

class ConversionService(
    config: ConversionConfig,
    source: ConversionSource,
    sink: ConversionSink,
    namer: Namer
)(implicit
    scheduler: Scheduler
) {
  implicit val timeout: Timeout = Timeout.durationToTimeout(config.timeout)

  private val conversionActor =
    ConversionActor.create(config.concurrency, source, sink)

  def createTask(url: Uri): Future[TaskInfo] = {
    val taskId = namer.makeTaskId()
    conversionActor.ask(
      ConversionMessage.CreateTask(
        taskId,
        url,
        config.resultDirectory.resolve(s"${taskId.id}.json"),
        _
      )
    )
  }
  def listTasks: Future[Seq[TaskShortInfo]] =
    conversionActor.ask(ConversionMessage.ListTasks)
  def getTask(taskId: TaskId): Future[Option[TaskInfo]] =
    conversionActor.ask(ConversionMessage.GetTask(taskId, _))
  def cancelTask(taskId: TaskId): Future[Boolean] =
    conversionActor.ask(ConversionMessage.CancelTask(taskId, _))
}
