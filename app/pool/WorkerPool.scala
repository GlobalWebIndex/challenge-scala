package pool

import pool.dependencies.{Cfg, Namer, Saver}
import pool.interface.{PoolMessage, TaskInfo, TaskShortInfo}
import pool.{PoolState, TaskRunState, WorkerFactory}

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, Scheduler}
import akka.util.Timeout

import scala.concurrent.Future

object WorkerPool {
  private sealed trait WorkerResponse
  private object WorkerResponse {
    object Cancelled extends WorkerResponse
    object Failed extends WorkerResponse
    final case class Done(totalCount: Long) extends WorkerResponse
  }
  private sealed trait Message[ID, IN, OUT]
  private object Message {
    final case class Public[ID, IN, OUT](
        message: PoolMessage[ID, IN, OUT]
    ) extends Message[ID, IN, OUT]
    final case class Private[ID, IN, OUT](taskId: ID, message: WorkerResponse)
        extends Message[ID, IN, OUT]
  }
}

class WorkerPool[CFG <: Cfg, ID, IN, OUT, ITEM](
    config: CFG,
    workerCreator: WorkerFactory[ID, IN, OUT],
    saver: Saver[CFG, ID, OUT, ITEM],
    namer: Namer[ID]
)(implicit
    scheduler: Scheduler
) {
  import WorkerPool._

  implicit val timeout: Timeout = Timeout.durationToTimeout(config.timeout)

  private val actor = ActorSystem(
    Behaviors
      .setup[Message[ID, IN, OUT]] { context =>
        implicit val as = context.system
        def createWorker(taskId: ID, url: IN, result: OUT) =
          workerCreator.createWorker(
            taskId,
            url,
            result,
            count =>
              context.self ! Message
                .Private(taskId, WorkerResponse.Done(count)),
            () => context.self ! Message.Private(taskId, WorkerResponse.Failed)
          )
        behavior(PoolState(config.concurrency, createWorker))
      }
      .transformMessages[PoolMessage[ID, IN, OUT]](Message.Public(_)),
    "PoolActor"
  )

  def createTask(url: IN): Future[TaskInfo[ID, OUT]] = {
    val taskId = namer.makeTaskId()
    actor.ask(
      PoolMessage.CreateTask(
        taskId,
        url,
        saver.target(config, taskId),
        _
      )
    )
  }
  def listTasks: Future[Seq[TaskShortInfo[ID]]] =
    actor.ask(PoolMessage.ListTasks(_))
  def getTask(taskId: ID): Future[Option[TaskInfo[ID, OUT]]] =
    actor.ask(PoolMessage.GetTask(taskId, _))
  def cancelTask(taskId: ID): Future[Boolean] =
    actor.ask(PoolMessage.CancelTask(taskId, _))

  private def behavior(
      state: PoolState[ID, IN, OUT]
  )(implicit timeout: Timeout): Behavior[Message[ID, IN, OUT]] =
    Behaviors.receive((context, message) =>
      message match {
        case Message.Public(message) =>
          message match {
            case PoolMessage.CreateTask(taskId, url, result, replyTo) =>
              val (taskInfo, newState) = state.addTask(taskId, url, result)
              replyTo ! taskInfo
              behavior(newState)
            case PoolMessage.ListTasks(replyTo) =>
              replyTo ! state.listTasks()
              Behaviors.same
            case PoolMessage.GetTask(taskId, replyTo) =>
              if (!state.getTask(taskId, replyTo ! Some(_))) replyTo ! None
              Behaviors.same
            case PoolMessage.CancelTask(taskId, replyTo) =>
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
              val newTaskState: TaskRunState[IN, OUT] = message match {
                case WorkerResponse.Cancelled =>
                  saver.unmake(result)
                  TaskRunState.Cancelled()
                case WorkerResponse.Failed =>
                  saver.unmake(result)
                  TaskRunState.Failed()
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
