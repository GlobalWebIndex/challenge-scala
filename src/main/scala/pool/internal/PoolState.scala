package pool.internal

import pool.WorkerFactory
import pool.interface.TaskCurrentState
import pool.interface.TaskFinishReason
import pool.interface.TaskInfo
import pool.interface.TaskShortInfo
import pool.interface.TaskState
import pool.internal.TaskRunState

import akka.actor.typed.ActorSystem
import akka.util.Timeout
object PoolState {
  final case class QueuedTask[ID, IN, OUT](taskId: ID, url: IN, result: OUT)
  def apply[ID, IN, OUT](
      concurrency: Int,
      workerFactory: WorkerFactory[ID, IN, OUT],
      reportFinish: (ID, Long, TaskFinishReason) => Unit,
      reportCount: (ID, Long) => Unit
  ): PoolState[ID, IN, OUT] =
    PoolState(
      concurrency,
      workerFactory,
      reportFinish,
      reportCount,
      0,
      Vector.empty,
      Map.empty
    )
}

final case class PoolState[ID, IN, OUT](
    concurrency: Int,
    workerFactory: WorkerFactory[ID, IN, OUT],
    reportFinish: (ID, Long, TaskFinishReason) => Unit,
    reportCount: (ID, Long) => Unit,
    running: Int,
    queue: Vector[PoolState.QueuedTask[ID, IN, OUT]],
    tasks: Map[ID, TaskRunState[IN, OUT]]
) {
  private def createWorker(
      taskId: ID,
      runningSince: Long,
      url: IN,
      result: OUT
  )(implicit as: ActorSystem[_]) =
    (taskId -> TaskRunState.Running[IN, OUT](
      runningSince,
      workerFactory.createWorker(
        taskId,
        url,
        result,
        reportFinish(taskId, _, TaskFinishReason.Done),
        reportFinish(taskId, _, TaskFinishReason.Failed)
      ),
      result,
      Vector.empty
    ))
  def addTask(
      taskId: ID,
      url: IN,
      result: OUT
  )(implicit as: ActorSystem[_]): (TaskInfo[ID, OUT], PoolState[ID, IN, OUT]) =
    if (running < concurrency || concurrency == 0) {
      val time = System.currentTimeMillis
      (
        TaskInfo(taskId, 0, time, TaskCurrentState.Running()),
        copy(
          running = running + 1,
          tasks = tasks + createWorker(taskId, time, url, result)
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
  ): (Boolean, Option[PoolState[ID, IN, OUT]]) = {
    val task = tasks.get(taskId)
    val newState = task.flatMap {
      case TaskRunState.Scheduled(_, _) =>
        onTaskInfo(TaskInfo(taskId, 0, 0, TaskCurrentState.Scheduled()))
        None
      case r @ TaskRunState.Running(_, _, _, _) =>
        r.worker.currentCount(reportCount(taskId, _))
        val newTaskState = r.copy[IN, OUT](countingRequests =
          r.countingRequests :+ (count =>
            onTaskInfo(
              TaskInfo(
                taskId,
                count,
                r.runningSince,
                TaskCurrentState.Running()
              )
            )
          )
        )
        Some(copy(tasks = tasks + (taskId -> newTaskState)))
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
        None
    }
    (task.isDefined, newState)
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
      reply: Long => Unit
  ): (Boolean, Option[PoolState[ID, IN, OUT]]) = {
    val task = tasks.get(taskId)
    val newState = task.flatMap {
      case r @ TaskRunState.Running(_, _, _, _) =>
        r.worker.cancel(reportFinish(taskId, _, TaskFinishReason.Cancelled))
        Some(
          copy(tasks =
            tasks + (taskId -> r.copy[IN, OUT](
              countingRequests = r.countingRequests :+ reply
            ))
          )
        )
      case TaskRunState.Scheduled(_, result) =>
        reply(0)
        Some(
          copy(
            queue = queue.filterNot(_.taskId == taskId),
            tasks = tasks + (taskId -> TaskRunState
              .Finished[IN, OUT](0, 0, 0, result, TaskFinishReason.Cancelled))
          )
        )
      case TaskRunState.Finished(_, _, linesProcessed, _, _) =>
        reply(linesProcessed)
        None
    }
    (task.isDefined, newState)
  }
  def finishTask(
      taskId: ID,
      totalCount: Long,
      reason: TaskFinishReason
  )(implicit
      timeout: Timeout,
      as: ActorSystem[_]
  ): Option[(OUT, PoolState[ID, IN, OUT])] =
    tasks.get(taskId) match {
      case Some(
            TaskRunState
              .Running(
                runningSince,
                _,
                result,
                countingRequests
              )
          ) =>
        val newTaskState = TaskRunState.Finished[IN, OUT](
          runningSince,
          System.currentTimeMillis,
          totalCount,
          result,
          reason
        )
        countingRequests.foreach(_(totalCount))
        val newTasks = tasks + (taskId -> newTaskState)
        val newState = queue.headOption match {
          case None => copy(running = running - 1, tasks = newTasks)
          case Some(task) =>
            copy(
              queue = queue.drop(1),
              tasks = newTasks + createWorker(
                task.taskId,
                System.currentTimeMillis,
                task.url,
                task.result
              )
            )
        }
        Some((result, newState))
      case _ => None
    }
  def taskCounted(taskId: ID, count: Long): Option[PoolState[ID, IN, OUT]] =
    tasks.get(taskId) match {
      case Some(r @ TaskRunState.Running(_, _, _, _)) =>
        r.countingRequests.foreach(_(count))
        Some(
          copy(
            tasks = tasks + (taskId -> r.copy(countingRequests = Vector.empty))
          )
        )
      case _ => None
    }

}
