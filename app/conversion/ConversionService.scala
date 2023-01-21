package conversion

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import models.ConversionMessage
import models.TaskId
import models.TaskInfo
import models.TaskShortInfo

import java.nio.file.Path
import scala.concurrent.Future

object ConversionService {
  private sealed trait WorkerResponse
  private object WorkerResponse {
    object Cancelled extends WorkerResponse
    object Failed extends WorkerResponse
    final case class Done(totalCount: Long) extends WorkerResponse
  }
  private sealed trait Message
  private object Message {
    final case class Public(message: ConversionMessage) extends Message
    final case class Private(taskId: TaskId, message: WorkerResponse)
        extends Message
  }
}

class ConversionService(
    config: ConversionConfig,
    workerCreator: ConversionWorkerFactory,
    sink: ConversionSink,
    namer: Namer
)(implicit
    scheduler: Scheduler
) {
  import ConversionService._

  implicit val timeout: Timeout = Timeout.durationToTimeout(config.timeout)

  private val actor = ActorSystem(
    Behaviors
      .setup[Message] { context =>
        implicit val as = context.system
        def createWorker(taskId: TaskId, url: Uri, result: Path) =
          workerCreator.createWorker(
            taskId,
            url,
            result,
            count =>
              context.self ! Message
                .Private(taskId, WorkerResponse.Done(count)),
            () => context.self ! Message.Private(taskId, WorkerResponse.Failed)
          )
        behavior(ConversionState(config.concurrency, createWorker))
      }
      .transformMessages[ConversionMessage](Message.Public(_)),
    "ConversionActor"
  )

  def createTask(url: Uri): Future[TaskInfo] = {
    val taskId = namer.makeTaskId()
    actor.ask(
      ConversionMessage.CreateTask(
        taskId,
        url,
        config.resultDirectory.resolve(s"${taskId.id}.json"),
        _
      )
    )
  }
  def listTasks: Future[Seq[TaskShortInfo]] =
    actor.ask(ConversionMessage.ListTasks)
  def getTask(taskId: TaskId): Future[Option[TaskInfo]] =
    actor.ask(ConversionMessage.GetTask(taskId, _))
  def cancelTask(taskId: TaskId): Future[Boolean] =
    actor.ask(ConversionMessage.CancelTask(taskId, _))

  private def behavior(
      state: ConversionState
  )(implicit timeout: Timeout): Behavior[Message] =
    Behaviors.receive((context, message) =>
      message match {
        case Message.Public(message) =>
          message match {
            case ConversionMessage.CreateTask(taskId, url, result, replyTo) =>
              val (taskInfo, newState) = state.addTask(taskId, url, result)
              replyTo ! taskInfo
              behavior(newState)
            case ConversionMessage.ListTasks(replyTo) =>
              replyTo ! state.listTasks
              Behaviors.same
            case ConversionMessage.GetTask(taskId, replyTo) =>
              if (!state.getTask(taskId, replyTo ! Some(_))) replyTo ! None
              Behaviors.same
            case ConversionMessage.CancelTask(taskId, replyTo) =>
              val (response, newStateOpt) = state.cancelTask(
                taskId,
                () =>
                  context.self ! Message.Private(
                    taskId,
                    WorkerResponse.Cancelled
                  )
              )
              replyTo ! response
              newStateOpt match {
                case None           => Behaviors.same
                case Some(newState) => behavior(newState)
              }
          }
        case Message.Private(taskId, message) =>
          state.tasks.get(taskId) match {
            case Some(TaskRunState.Running(runningSince, _, result, _)) =>
              implicit val system = context.system
              val newTaskState = message match {
                case WorkerResponse.Cancelled =>
                  sink.unmake(result)
                  TaskRunState.Cancelled
                case WorkerResponse.Failed =>
                  sink.unmake(result)
                  TaskRunState.Failed
                case WorkerResponse.Done(totalCount) =>
                  TaskRunState.Done(
                    runningSince,
                    System.currentTimeMillis,
                    totalCount,
                    result
                  )
              }
              behavior(state.pickNext(taskId, newTaskState))
            case _ => Behaviors.same
          }
      }
    )
}
