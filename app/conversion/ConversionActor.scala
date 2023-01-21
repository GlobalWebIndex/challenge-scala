package conversion

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import models.ConversionMessage
import models.TaskCurrentState
import models.TaskId
import models.TaskInfo
import models.TaskShortInfo
import models.TaskState

import java.nio.file.Files
import java.nio.file.Path
import akka.http.scaladsl.model.Uri

object ConversionActor {
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
  private sealed trait TaskRunState
  private object TaskRunState {
    final case class Scheduled(url: Uri, result: Path) extends TaskRunState
    final case class Running(
        runningSince: Long,
        worker: ConversionWorker,
        result: Path,
        cancellationInProgress: Boolean
    ) extends TaskRunState
    object Cancelled extends TaskRunState
    object Failed extends TaskRunState
    final case class Done(
        runningSince: Long,
        finishedAt: Long,
        linesProcessed: Long,
        result: Path
    ) extends TaskRunState
  }
  private final case class QueuedTask(taskId: TaskId, url: Uri, result: Path)
  private final case class State(
      concurrency: Int,
      createWorker: (TaskId, Uri, Path) => ConversionWorker,
      running: Int,
      queue: Vector[QueuedTask],
      tasks: Map[TaskId, TaskRunState]
  ) {
    def addTask(taskId: TaskId, url: Uri, result: Path): (TaskInfo, State) =
      if (running < concurrency) {
        val time = System.currentTimeMillis
        (
          TaskInfo(taskId, 0, time, TaskCurrentState.Running),
          copy(
            running = running + 1,
            tasks = tasks + (taskId -> TaskRunState.Running(
              time,
              createWorker(taskId, url, result),
              result,
              false
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
        taskId: TaskId,
        onTaskInfo: TaskInfo => Unit
    ): Boolean = {
      val task = tasks.get(taskId)
      task.foreach {
        case TaskRunState.Scheduled(_, _) =>
          onTaskInfo(TaskInfo(taskId, 0, 0, TaskCurrentState.Scheduled))
        case TaskRunState.Running(runningSince, worker, _, _) =>
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
        case TaskRunState.Failed =>
          onTaskInfo(TaskInfo(taskId, 0, 0, TaskCurrentState.Failed))
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
      task.isDefined
    }
    def listTasks: Seq[TaskShortInfo] = tasks.toSeq.map {
      case (taskId, state) =>
        state match {
          case TaskRunState.Scheduled(_, _) =>
            TaskShortInfo(taskId, TaskState.SCHEDULED)
          case TaskRunState.Running(_, _, _, _) =>
            TaskShortInfo(taskId, TaskState.RUNNING)
          case TaskRunState.Cancelled =>
            TaskShortInfo(taskId, TaskState.CANCELLED)
          case TaskRunState.Failed =>
            TaskShortInfo(taskId, TaskState.FAILED)
          case TaskRunState.Done(_, finishedAt, _, result) =>
            TaskShortInfo(taskId, TaskState.DONE)
        }
    }
    def cancelTask(
        taskId: TaskId,
        onCancel: () => Unit
    ): (Boolean, Option[State]) = {
      val task = tasks.get(taskId)
      val newState = task.flatMap {
        case TaskRunState.Running(s, worker, r, cancellationInProgress) =>
          if (cancellationInProgress) None
          else {
            worker.cancel(onCancel)
            Some(
              copy(tasks =
                tasks + (taskId -> TaskRunState.Running(s, worker, r, true))
              )
            )
          }
        case TaskRunState.Scheduled(_, _) =>
          Some(
            copy(
              queue = queue.filterNot(_.taskId == taskId),
              tasks = tasks + (taskId -> TaskRunState.Cancelled)
            )
          )
        case TaskRunState.Cancelled | TaskRunState.Failed |
            TaskRunState.Done(_, _, _, _) =>
          None
      }
      (task.isDefined, newState)
    }
    def pickNext(
        self: ActorRef[Message],
        taskId: TaskId,
        newTaskState: TaskRunState
    )(implicit timeout: Timeout, as: ActorSystem[_]): State = {
      val newTasks = tasks + (taskId -> newTaskState)
      queue.headOption match {
        case None => copy(running = running - 1, tasks = newTasks)
        case Some(task) =>
          copy(
            queue = queue.drop(1),
            tasks = newTasks + (task.taskId -> TaskRunState.Running(
              System.currentTimeMillis,
              createWorker(task.taskId, task.url, task.result),
              task.result,
              false
            ))
          )
      }
    }
  }
  private def behavior(
      state: State
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
                  Files.deleteIfExists(result)
                  TaskRunState.Cancelled
                case WorkerResponse.Failed =>
                  Files.deleteIfExists(result)
                  TaskRunState.Failed
                case WorkerResponse.Done(totalCount) =>
                  TaskRunState.Done(
                    runningSince,
                    System.currentTimeMillis,
                    totalCount,
                    result
                  )
              }
              behavior(state.pickNext(context.self, taskId, newTaskState))
            case _ => Behaviors.same
          }
      }
    )
  def create(
      concurrency: Int,
      source: ConversionSource,
      sink: ConversionSink
  )(implicit timeout: Timeout): ActorRef[ConversionMessage] = {
    ActorSystem(
      Behaviors
        .setup[Message] { context =>
          implicit val as = context.system
          def createWorker(taskId: TaskId, url: Uri, result: Path) =
            new ConversionWorker(
              source.make(url),
              sink.make(result),
              count =>
                context.self ! Message
                  .Private(taskId, WorkerResponse.Done(count)),
              () =>
                context.self ! Message.Private(taskId, WorkerResponse.Failed)
            )
          behavior(State(concurrency, createWorker, 0, Vector.empty, Map.empty))
        }
        .transformMessages[ConversionMessage](Message.Public(_)),
      "ConversionActor"
    )
  }
}
