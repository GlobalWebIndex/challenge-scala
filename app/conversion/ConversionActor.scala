package conversion

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import models.ConversionMessage
import models.QueuedTask
import models.TaskCurrentState
import models.TaskInfo
import models.TaskShortInfo

import java.io.File
import models.TaskState

object ConversionActor {
  private sealed trait WorkerResponse
  private object WorkerResponse {
    object Cancelled extends WorkerResponse
    final case class Done(totalCount: Long) extends WorkerResponse
  }
  private sealed trait Message
  private object Message {
    final case class Public(message: ConversionMessage) extends Message
    final case class Private(taskId: String, message: WorkerResponse)
        extends Message
  }
  sealed trait TaskRunState
  object TaskRunState {
    final case class Scheduled(url: String, result: File) extends TaskRunState
    final case class Running(
        runningSince: Long,
        worker: ConversionWorker,
        result: File
    ) extends TaskRunState
    object Cancelled extends TaskRunState
    final case class Done(
        runningSince: Long,
        finishedAt: Long,
        linesProcessed: Long,
        result: File
    ) extends TaskRunState
  }
  private final case class State(
      concurrency: Int,
      running: Int,
      queue: Vector[QueuedTask],
      tasks: Map[String, TaskRunState]
  ) {
    def addTask(
        taskId: String,
        url: String,
        result: File,
        onDone: Long => Unit
    ): (TaskInfo, State) =
      if (running < concurrency) {
        val time = System.currentTimeMillis
        (
          TaskInfo(taskId, 0, time, TaskCurrentState.Running),
          copy(
            running = running + 1,
            tasks = tasks + (taskId -> TaskRunState.Running(
              time,
              new ConversionWorker(url, result, onDone),
              result
            ))
          )
        )
      } else {
        (
          TaskInfo(taskId, 0, 0, TaskCurrentState.Scheduled),
          copy(
            queue = queue :+ QueuedTask(taskId, url, result),
            tasks = tasks + (taskId -> TaskRunState.Scheduled(url, result))
          )
        )
      }
    def getTask(
        taskId: String,
        onTaskInfo: TaskInfo => Unit
    ): Boolean = {
      val task = tasks.get(taskId)
      task.foreach(_ match {
        case TaskRunState.Scheduled(_, _) =>
          onTaskInfo(TaskInfo(taskId, 0, 0, TaskCurrentState.Scheduled))
        case TaskRunState.Running(runningSince, worker, _) =>
          worker.currentCount(count =>
            onTaskInfo(
              TaskInfo(
                taskId,
                count,
                runningSince,
                TaskCurrentState.Running
              )
            )
          )
        case TaskRunState.Cancelled =>
          onTaskInfo(TaskInfo(taskId, 0, 0, TaskCurrentState.Cancelled))
        case TaskRunState.Done(
              runningSince,
              finishedAt,
              linesProcessed,
              result
            ) =>
          onTaskInfo(
            TaskInfo(
              taskId,
              linesProcessed,
              runningSince,
              TaskCurrentState.Done(finishedAt, result)
            )
          )
      })
      task.isDefined
    }
    def listTasks: Seq[TaskShortInfo] = tasks.toSeq.map {
      case (taskId, state) =>
        state match {
          case TaskRunState.Scheduled(_, _) =>
            TaskShortInfo(taskId, TaskState.SCHEDULED)
          case TaskRunState.Running(_, _, _) =>
            TaskShortInfo(taskId, TaskState.RUNNING)
          case TaskRunState.Cancelled =>
            TaskShortInfo(taskId, TaskState.CANCELLED)
          case TaskRunState.Done(_, finishedAt, _, result) =>
            TaskShortInfo(taskId, TaskState.DONE)
        }
    }
    def cancelTask(
        taskId: String,
        onCancel: () => Unit
    ): (Boolean, Option[State]) =
      tasks.get(taskId) match {
        case None => (false, None)
        case Some(st) =>
          st match {
            case TaskRunState.Running(_, worker, _) =>
              worker.cancel(onCancel)
              (true, None)
            case TaskRunState.Scheduled(_, _) =>
              val newState = copy(
                queue = queue.filterNot(_.taskId == taskId),
                tasks = tasks + (taskId -> TaskRunState.Cancelled)
              )
              (true, Some(newState))
            case TaskRunState.Cancelled        => (true, None)
            case TaskRunState.Done(_, _, _, _) => (true, None)
          }
      }
    def pickNext(
        taskId: String,
        newTaskState: TaskRunState,
        onDone: (String, Long) => Unit
    ): State = {
      val newTasks = tasks + (taskId -> newTaskState)
      queue.headOption match {
        case None => copy(running = running - 1, tasks = newTasks)
        case Some(newTask) =>
          copy(
            queue = queue.drop(1),
            tasks = newTasks + (newTask.taskId -> TaskRunState.Running(
              System.currentTimeMillis,
              new ConversionWorker(
                newTask.url,
                newTask.result,
                onDone(newTask.taskId, _)
              ),
              newTask.result
            ))
          )
      }
    }
  }
  private def done(taskId: String, totalCount: Long) =
    Message.Private(taskId, WorkerResponse.Done(totalCount))
  private def behavior(state: State): Behavior[Message] =
    Behaviors.receive((context, message) =>
      message match {
        case Message.Public(message) =>
          message match {
            case ConversionMessage.CreateTask(taskId, url, result, replyTo) =>
              val (taskInfo, newState) =
                state.addTask(
                  taskId,
                  url,
                  result,
                  context.self ! done(taskId, _)
                )
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
            case Some(TaskRunState.Running(runningSince, _, result)) =>
              val newTaskState = message match {
                case WorkerResponse.Cancelled =>
                  result.delete()
                  TaskRunState.Cancelled
                case WorkerResponse.Done(totalCount) =>
                  TaskRunState.Done(
                    runningSince,
                    System.currentTimeMillis,
                    totalCount,
                    result
                  )
              }
              behavior(
                state.pickNext(taskId, newTaskState, context.self ! done(_, _))
              )
            case _ => Behaviors.same
          }
      }
    )
  def create(concurrency: Int): ActorRef[ConversionMessage] =
    ActorSystem(
      behavior(State(concurrency, 0, Vector.empty, Map.empty))
        .transformMessages[ConversionMessage](Message.Public(_)),
      "ConversionActor"
    )
}
