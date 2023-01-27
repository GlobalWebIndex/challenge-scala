package pool.akka

import org.slf4j.Logger
import pool.WorkerFactory
import pool.WorkerPool
import pool.akka.internal.PoolMessage
import pool.akka.internal.PoolState
import pool.dependencies.Config
import pool.dependencies.Destination
import pool.dependencies.Namer
import pool.dependencies.Saver
import pool.interface.TaskFinishReason
import pool.interface.TaskInfo
import pool.interface.TaskShortInfo

import akka.Done
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.Future

/** Factory for [[WorkerPool]] */
object AkkaWorkerPool {
  private sealed trait Message[ID, IN, OUT]
  private object Message {
    final case class Public[ID, IN, OUT](
        message: PoolMessage[ID, IN, OUT]
    ) extends Message[ID, IN, OUT]
    final case class TaskFinished[ID, IN, OUT](
        taskId: ID,
        totalCount: Long,
        message: TaskFinishReason
    ) extends Message[ID, IN, OUT]
    final case class TaskCounted[ID, IN, OUT](taskId: ID, count: Long)
        extends Message[ID, IN, OUT]
  }

  /** Create a new worker pool
    *
    * @param config
    *   Pool configuration
    * @param log
    *   Logger to use for debugging purposes
    * @param workerFactory
    *   Factory for creating new workers — normally [[AkkaWorkerFactory.apply]]
    *   should be enough
    * @param saver
    *   Utilities for dealing with destinations
    * @param namer
    *   Task identifiers generator
    * @param actorName
    *   Name for the actor backing up this pool
    * @return
    *   The newly created [[WorkerPool]]
    */
  def apply[ID, IN, OUT <: Destination[_]](
      config: Config,
      log: Logger,
      workerFactory: WorkerFactory[IN, OUT],
      saver: Saver[ID, OUT],
      namer: Namer[ID],
      actorName: String
  )(implicit ctx: ActorContext[_]): WorkerPool[ID, IN, OUT] =
    new WorkerPool[ID, IN, OUT] {
      private implicit val timeout = Timeout.durationToTimeout(config.timeout)
      private implicit val actorSystem = ctx.system

      private val actor = ctx.spawn(
        Behaviors
          .setup[Message[ID, IN, OUT]] { context =>
            log.info(s"Worker pool $actorName created")
            behavior(
              PoolState[ID, IN, OUT](
                config.concurrency,
                workerFactory,
                context.self ! Message.TaskFinished(_, _, _),
                context.self ! Message.TaskCounted(_, _)
              )
            )
          }
          .transformMessages[PoolMessage[ID, IN, OUT]](Message.Public(_)),
        actorName
      )

      def createTask(source: => IN): Future[Option[TaskInfo[ID, OUT]]] = {
        val taskId = namer.makeTaskId()
        actor.ask(
          PoolMessage.CreateTask(
            taskId,
            () => source,
            saver.make(taskId),
            _
          )
        )
      }
      def listTasks: Future[Seq[TaskShortInfo[ID]]] =
        actor.ask(PoolMessage.ListTasks(_))
      def getTask(taskId: ID): Future[Option[TaskInfo[ID, OUT]]] =
        actor.ask(PoolMessage.GetTask(taskId, _))
      def cancelTask(taskId: ID): Future[Option[Long]] =
        actor.ask(PoolMessage.CancelTask(taskId, _))
      def cancelAll(): Future[Unit] = actor.ask(PoolMessage.CancelAll(_))

      private def behavior(
          state: PoolState[ID, IN, OUT]
      )(implicit timeout: Timeout): Behavior[Message[ID, IN, OUT]] =
        Behaviors.receiveMessage { message =>
          val newStateOpt = message match {
            case Message.Public(message) =>
              message match {
                case PoolMessage.CreateTask(
                      taskId,
                      source,
                      destination,
                      replyTo
                    ) =>
                  val added = state.addTask(taskId, source, destination)
                  replyTo ! added.map(_._1)
                  added.map(_._2)
                case PoolMessage.ListTasks(replyTo) =>
                  replyTo ! state.listTasks()
                  None
                case PoolMessage.GetTask(taskId, replyTo) =>
                  val (response, newState) =
                    state.getTask(taskId, replyTo ! Some(_))
                  if (!response) replyTo ! None
                  newState
                case PoolMessage.CancelTask(taskId, replyTo) =>
                  val (response, newState) =
                    state.cancelTask(taskId, replyTo ! Some(_))
                  if (!response) replyTo ! None
                  newState
                case PoolMessage.CancelAll(replyTo) =>
                  val newState = state.cancelAll(() => replyTo ! Done)
                  Some(newState)
              }
            case Message.TaskFinished(taskId, totalCount, reason) =>
              log.debug(
                s"Task $taskId is over — $totalCount items processed, final result: $reason"
              )
              state.finishTask(taskId, totalCount, reason).map {
                case (newTaskId, newState) =>
                  newTaskId.foreach(taskId =>
                    log.debug(s"Starting scheduled task $taskId")
                  )
                  newState
              }
            case Message.TaskCounted(taskId, count) =>
              log.debug(s"Task $taskId has processed $count items")
              state.taskCounted(taskId, count)
          }
          newStateOpt.map(behavior).getOrElse(Behaviors.same)
        }
    }
}
