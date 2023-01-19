package conversion

import akka.actor.typed.ActorRef
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import models.ConversionMessage
import models.TaskCurrentState
import models.TaskInfo

import java.io.File
import scala.concurrent.Future

class ConversionService(config: ConversionConfig)(implicit
    scheduler: Scheduler
) {
  implicit val timeout: Timeout = Timeout.durationToTimeout(config.timeout)

  private val conversionActor = ConversionActor.create(config.concurrency)

  def createTask(url: String): Future[TaskInfo] =
    conversionActor.ask(ConversionMessage.CreateTask(url, _))
  def listTasks: Future[Seq[TaskInfo]] =
    conversionActor.ask(ConversionMessage.ListTasks)
  def getTask(taskId: String): Future[Option[TaskInfo]] =
    conversionActor.ask(ConversionMessage.GetTask(taskId, _))
  def cancelTask(taskId: String): Future[Boolean] =
    conversionActor.ask(ConversionMessage.CancelTask(taskId, _))
}
