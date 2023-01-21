package pool

import org.slf4j.Logger
import pool.dependencies.Cfg
import pool.dependencies.Namer
import pool.dependencies.Saver
import pool.interface.PoolMessage
import pool.interface.TaskFinishReason
import pool.interface.TaskInfo
import pool.interface.TaskShortInfo

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.Future

object WorkerPool {
  private sealed trait Message[ID, IN, OUT]
  private object Message {
    final case class Public[ID, IN, OUT](
        message: PoolMessage[ID, IN, OUT]
    ) extends Message[ID, IN, OUT]
    final case class Private[ID, IN, OUT](
        taskId: ID,
        totalCount: Long,
        message: TaskFinishReason
    ) extends Message[ID, IN, OUT]
  }
}

class WorkerPool[CFG <: Cfg, ID, IN, OUT, ITEM](
    config: CFG,
    log: Logger,
    workerCreator: WorkerFactory[ID, IN, OUT],
    saver: Saver[CFG, ID, OUT, ITEM],
    namer: Namer[ID],
    actorName: String
)(implicit
    ctx: ActorContext[_]
) {
  import WorkerPool._

  private implicit val timeout = Timeout.durationToTimeout(config.timeout)
  private implicit val actorSystem = ctx.system

  private val actor = ctx.spawn(
    Behaviors
      .setup[Message[ID, IN, OUT]] { context =>
        def createWorker(taskId: ID, url: IN, result: OUT) =
          workerCreator.createWorker(
            taskId,
            url,
            result,
            context.self ! Message.Private(taskId, _, TaskFinishReason.Done),
            context.self ! Message.Private(taskId, _, TaskFinishReason.Failed)
          )
        log.info(s"Worker pool $actorName created")
        behavior(PoolState(config.concurrency, createWorker))
      }
      .transformMessages[PoolMessage[ID, IN, OUT]](Message.Public(_)),
    actorName
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
                context.self ! Message.Private(
                  taskId,
                  _,
                  TaskFinishReason.Cancelled
                )
              )
              replyTo ! response
              newStateOpt match {
                case None           => Behaviors.same
                case Some(newState) => behavior(newState)
              }
          }
        case Message.Private(taskId, totalCount, reason) =>
          log.debug(
            s"Task $taskId is over — $totalCount items processed, final result: ${reason.getClass().getSimpleName()}"
          )
          state.tasks.get(taskId) match {
            case Some(TaskRunState.Running(runningSince, _, result, _)) =>
              saver.unmake(result, reason)
              val newTaskState = TaskRunState.Finished[IN, OUT](
                runningSince,
                System.currentTimeMillis,
                totalCount,
                result,
                reason
              )
              behavior(state.pickNext(taskId, newTaskState))
            case _ => Behaviors.same
          }
      }
    )
}
