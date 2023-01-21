package conversion

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import models.TaskCurrentState
import models.TaskId
import models.TaskInfo
import models.TaskShortInfo
import models.TaskState

import java.nio.file.Path

object ConversionState {
  final case class QueuedTask(taskId: TaskId, url: Uri, result: Path)
  def apply(
      concurrency: Int,
      createWorker: (TaskId, Uri, Path) => ConversionWorker
  ): ConversionState =
    ConversionState(concurrency, createWorker, 0, Vector.empty, Map.empty)
}

final case class ConversionState(
    concurrency: Int,
    createWorker: (TaskId, Uri, Path) => ConversionWorker,
    running: Int,
    queue: Vector[ConversionState.QueuedTask],
    tasks: Map[TaskId, TaskRunState]
) {
  def addTask(
      taskId: TaskId,
      url: Uri,
      result: Path
  ): (TaskInfo, ConversionState) =
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
          queue = queue :+ ConversionState.QueuedTask(taskId, url, result),
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
  def listTasks: Seq[TaskShortInfo] = tasks.toSeq.map { case (taskId, state) =>
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
  ): (Boolean, Option[ConversionState]) = {
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
      taskId: TaskId,
      newTaskState: TaskRunState
  )(implicit timeout: Timeout, as: ActorSystem[_]): ConversionState = {
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
