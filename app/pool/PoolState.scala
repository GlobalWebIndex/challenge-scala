package pool

import pool.TaskRunState
import pool.Worker
import pool.interface.TaskCurrentState
import pool.interface.TaskFinishReason
import pool.interface.TaskInfo
import pool.interface.TaskShortInfo
import pool.interface.TaskState

import akka.actor.typed.ActorSystem
import akka.util.Timeout

object PoolState {
  final case class QueuedTask[ID, IN, OUT](taskId: ID, url: IN, result: OUT)
  def apply[ID, IN, OUT](
      concurrency: Int,
      createWorker: (ID, IN, OUT) => Worker
  ): PoolState[ID, IN, OUT] =
    PoolState(concurrency, createWorker, 0, Vector.empty, Map.empty)
}

final case class PoolState[ID, IN, OUT](
    concurrency: Int,
    createWorker: (ID, IN, OUT) => Worker,
    running: Int,
    queue: Vector[PoolState.QueuedTask[ID, IN, OUT]],
    tasks: Map[ID, TaskRunState[IN, OUT]]
) {
  def addTask(
      taskId: ID,
      url: IN,
      result: OUT
  ): (TaskInfo[ID, OUT], PoolState[ID, IN, OUT]) =
    if (running < concurrency) {
      val time = System.currentTimeMillis
      (
        TaskInfo(taskId, 0, time, TaskCurrentState.Running()),
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
        TaskInfo(taskId, 0, 0, TaskCurrentState.Scheduled()),
        copy(
          queue = queue :+ PoolState.QueuedTask(taskId, url, result),
          tasks = tasks + (taskId -> TaskRunState.Scheduled(url, result))
        )
      )
    }
  def getTask(
      taskId: ID,
      onTaskInfo: TaskInfo[ID, OUT] => Unit
  ): Boolean = {
    val task = tasks.get(taskId)
    task.foreach {
      case TaskRunState.Scheduled(_, _) =>
        onTaskInfo(TaskInfo(taskId, 0, 0, TaskCurrentState.Scheduled()))
      case TaskRunState.Running(runningSince, worker, _, _) =>
        worker.currentCount(count =>
          onTaskInfo(
            TaskInfo(
              taskId,
              count,
              runningSince,
              TaskCurrentState.Running()
            )
          )
        )
      case TaskRunState.Finished(
            runningSince,
            finishedAt,
            linesProcessed,
            result,
            reason
          ) =>
        onTaskInfo(
          TaskInfo(
            taskId,
            linesProcessed,
            runningSince,
            TaskCurrentState.Finished(finishedAt, result, reason)
          )
        )
    }
    task.isDefined
  }
  def listTasks(): Seq[TaskShortInfo[ID]] = tasks.toSeq.map {
    case (taskId, state) =>
      state match {
        case TaskRunState.Scheduled(_, _) =>
          TaskShortInfo(taskId, TaskState.SCHEDULED)
        case TaskRunState.Running(_, _, _, _) =>
          TaskShortInfo(taskId, TaskState.RUNNING)
        case TaskRunState.Finished(_, _, _, _, reason) =>
          reason match {
            case TaskFinishReason.Cancelled =>
              TaskShortInfo(taskId, TaskState.CANCELLED)
            case TaskFinishReason.Failed =>
              TaskShortInfo(taskId, TaskState.FAILED)
            case TaskFinishReason.Done =>
              TaskShortInfo(taskId, TaskState.DONE)
          }
      }
  }
  def cancelTask(
      taskId: ID,
      onCancel: Long => Unit
  ): (Boolean, Option[PoolState[ID, IN, OUT]]) = {
    val task = tasks.get(taskId)
    val newState = task.flatMap {
      case TaskRunState.Running(s, worker, r, cancellationInProgress) =>
        if (cancellationInProgress) None
        else {
          worker.cancel(onCancel)
          Some(
            copy(tasks =
              tasks + (taskId -> TaskRunState
                .Running[IN, OUT](s, worker, r, true))
            )
          )
        }
      case TaskRunState.Scheduled(_, result) =>
        Some(
          copy(
            queue = queue.filterNot(_.taskId == taskId),
            tasks = tasks + (taskId -> TaskRunState
              .Finished[IN, OUT](0, 0, 0, result, TaskFinishReason.Cancelled))
          )
        )
      case TaskRunState.Finished(_, _, _, _, _) =>
        None
    }
    (task.isDefined, newState)
  }
  def pickNext(
      taskId: ID,
      newTaskState: TaskRunState[IN, OUT]
  )(implicit timeout: Timeout, as: ActorSystem[_]): PoolState[ID, IN, OUT] = {
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
