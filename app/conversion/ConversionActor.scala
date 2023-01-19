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
    final case class Cancelled(taskId: String) extends WorkerResponse
    final case class Done(taskId: String, totalCount: Long)
        extends WorkerResponse
  }
  private sealed trait Message
  private object Message {
    final case class Public(message: ConversionMessage) extends Message
    final case class Private(message: WorkerResponse) extends Message
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
        val runState =
          TaskRunState.Running(
            time,
            new ConversionWorker(
              url,
              result,
              onDone
            ),
            result
          )
        (
          TaskInfo(taskId, 0, time, TaskCurrentState.Running),
          copy(
            running = running + 1,
            tasks = tasks + (taskId -> runState)
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
    ): Boolean =
      tasks.get(taskId) match {
        case None => false
        case Some(st) =>
          st match {
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
          }
          true
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
    private def cancelScheduledTask(taskId: String): State = copy(
      queue = queue.filterNot(_.taskId == taskId),
      tasks = tasks + (taskId -> TaskRunState.Cancelled)
    )
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
              (true, Some(cancelScheduledTask(taskId)))
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
  private def behavior(
      context: ActorContext[Message],
      state: State
  ): Behavior[Message] =
    Behaviors.receiveMessage(_ match {
      case Message.Public(message) =>
        message match {
          case ConversionMessage.CreateTask(taskId, url, result, replyTo) =>
            val (taskInfo, newState) =
              state.addTask(
                taskId,
                url,
                result,
                totalCount =>
                  context.self ! Message.Private(
                    WorkerResponse.Done(taskId, totalCount)
                  )
              )
            replyTo ! taskInfo
            behavior(context, newState)
          case ConversionMessage.ListTasks(replyTo) =>
            replyTo ! state.listTasks
            Behaviors.same
          case ConversionMessage.GetTask(taskId, replyTo) =>
            if (!state.getTask(taskId, taskInfo => replyTo ! Some(taskInfo)))
              replyTo ! None
            Behaviors.same
          case ConversionMessage.CancelTask(taskId, replyTo) =>
            val (response, newStateOpt) = state.cancelTask(
              taskId,
              () =>
                context.self ! Message.Private(
                  WorkerResponse.Cancelled(taskId)
                )
            )
            replyTo ! response
            newStateOpt match {
              case None           => Behaviors.same
              case Some(newState) => behavior(context, newState)
            }
        }
      case Message.Private(message) =>
        message match {
          case WorkerResponse.Cancelled(taskId) =>
            state.tasks.get(taskId) match {
              case Some(TaskRunState.Running(_, _, result)) =>
                result.delete()
                behavior(
                  context,
                  state.pickNext(
                    taskId,
                    TaskRunState.Cancelled,
                    (newTaskId, totalCount) =>
                      context.self ! Message.Private(
                        WorkerResponse.Done(newTaskId, totalCount)
                      )
                  )
                )
              case _ => Behaviors.same
            }
          case WorkerResponse.Done(taskId, totalCount) =>
            state.tasks.get(taskId) match {
              case Some(TaskRunState.Running(runningSince, _, result)) =>
                behavior(
                  context,
                  state.pickNext(
                    taskId,
                    TaskRunState.Done(
                      runningSince,
                      System.currentTimeMillis,
                      totalCount,
                      result
                    ),
                    (newTaskId, totalCount) =>
                      context.self ! Message.Private(
                        WorkerResponse.Done(newTaskId, totalCount)
                      )
                  )
                )
              case _ => Behaviors.same
            }
        }
    })
  def create(concurrency: Int): ActorRef[ConversionMessage] =
    ActorSystem(
      Behaviors
        .setup[Message](context =>
          behavior(context, State(concurrency, 0, Vector.empty, Map.empty))
        )
        .transformMessages[ConversionMessage](Message.Public(_)),
      "ConversionActor"
    )
}
