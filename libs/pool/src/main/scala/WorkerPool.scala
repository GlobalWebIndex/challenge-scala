package pool

import org.slf4j.Logger
import pool.WorkerFactory
import pool.dependencies.Config
import pool.dependencies.Namer
import pool.dependencies.Saver
import pool.interface.TaskFinishReason
import pool.interface.TaskInfo
import pool.interface.TaskShortInfo
import pool.internal.PoolMessage
import pool.internal.PoolState

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.Future

/** Schedules the tasks.
  *
  * Each task copies a stream of items from a given source to a given
  * destination
  * @tparam ID
  *   Unique task identifiers
  * @tparam IN
  *   Source address, such as URL
  * @tparam OUT
  *   Destination address, such as filename
  */
trait WorkerPool[ID, IN, OUT] {

  /** Adds a task to the pool
    * @param url
    *   Source address
    * @return
    *   Information about the added task, including it's identifier
    */
  def createTask(url: IN): Future[TaskInfo[ID, OUT]]

  /** Lists all added tasks
    *
    * @return
    *   Abbreviated information about all the tasks
    */
  def listTasks: Future[Seq[TaskShortInfo[ID]]]

  /** Provides streaming information about the specified task
    *
    * @param taskId
    *   Task identifier
    * @return
    *   Information about that task, if it exists
    */
  def getTask(taskId: ID): Future[Option[TaskInfo[ID, OUT]]]

  /** Cancels, but not removes, the task from the pool
    *
    * @param taskId
    *   Task identifier
    * @return
    *   The number of processed items, if the task exists
    */
  def cancelTask(taskId: ID): Future[Option[Long]]
}

/** Factory for [[WorkerPool]] */
object WorkerPool {
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
    *   Factory for creating new workers — normally [[WorkerFactory.apply]]
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
  def apply[ID, IN, OUT, ITEM](
      config: Config,
      log: Logger,
      workerFactory: WorkerFactory[ID, IN, OUT],
      saver: Saver[ID, OUT, ITEM],
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

      def createTask(url: IN): Future[TaskInfo[ID, OUT]] = {
        val taskId = namer.makeTaskId()
        actor.ask(PoolMessage.CreateTask(taskId, url, saver.target(taskId), _))
      }
      def listTasks: Future[Seq[TaskShortInfo[ID]]] =
        actor.ask(PoolMessage.ListTasks(_))
      def getTask(taskId: ID): Future[Option[TaskInfo[ID, OUT]]] =
        actor.ask(PoolMessage.GetTask(taskId, _))
      def cancelTask(taskId: ID): Future[Option[Long]] =
        actor.ask(PoolMessage.CancelTask(taskId, _))

      private def behavior(
          state: PoolState[ID, IN, OUT]
      )(implicit timeout: Timeout): Behavior[Message[ID, IN, OUT]] =
        Behaviors.receiveMessage { message =>
          val newStateOpt = message match {
            case Message.Public(message) =>
              message match {
                case PoolMessage.CreateTask(taskId, url, result, replyTo) =>
                  val (taskInfo, newState) = state.addTask(taskId, url, result)
                  replyTo ! taskInfo
                  Some(newState)
                case PoolMessage.ListTasks(replyTo) =>
                  replyTo ! state.listTasks()
                  None
                case PoolMessage.GetTask(taskId, replyTo) =>
                  val (response, newState) =
                    state.getTask(taskId, replyTo ! Some(_))
                  if (!response) replyTo ! None
                  newState
                case PoolMessage.CancelTask(taskId, replyTo) =>
                  val (response, newState) = state.cancelTask(
                    taskId,
                    replyTo ! Some(_)
                  )
                  if (!response) replyTo ! None
                  newState
              }
            case Message.TaskFinished(taskId, totalCount, reason) =>
              log.debug(
                s"Task $taskId is over — $totalCount items processed, final result: ${reason.getClass().getSimpleName()}"
              )
              state.finishTask(taskId, totalCount, reason).map {
                case (result, newState) =>
                  saver.unmake(result, reason)
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
