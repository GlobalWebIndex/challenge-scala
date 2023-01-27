package pool.akka.internal

import pool.WorkerFactory
import pool.akka.internal.TaskRunState
import pool.dependencies.Destination
import pool.interface.TaskCurrentState
import pool.interface.TaskFinishReason
import pool.interface.TaskInfo
import pool.interface.TaskShortInfo
import pool.interface.TaskState

object PoolState {
  final case class QueuedTask[ID, IN, OUT](
      taskId: ID,
      source: () => IN,
      destination: OUT
  )
  def apply[ID, IN, OUT <: Destination[_]](
      concurrency: Int,
      workerFactory: WorkerFactory[IN, OUT],
      reportFinish: (ID, Long, TaskFinishReason) => Unit,
      reportCount: (ID, Long) => Unit
  ): PoolState[ID, IN, OUT] =
    PoolState(
      concurrency,
      workerFactory,
      reportFinish,
      reportCount,
      None,
      0,
      Vector.empty,
      Map.empty
    )
}

final case class PoolState[ID, IN, OUT <: Destination[_]](
    concurrency: Int,
    workerFactory: WorkerFactory[IN, OUT],
    reportFinish: (ID, Long, TaskFinishReason) => Unit,
    reportCount: (ID, Long) => Unit,
    stopRequest: Option[() => Unit],
    running: Int,
    queue: Vector[PoolState.QueuedTask[ID, IN, OUT]],
    tasks: Map[ID, TaskRunState[OUT]]
) {
  private def createWorker(
      taskId: ID,
      runningSince: Long,
      source: IN,
      destination: OUT
  ) = (taskId -> TaskRunState.Running[OUT](
    runningSince,
    workerFactory.createWorker(
      source,
      destination,
      reportFinish(taskId, _, TaskFinishReason.Done),
      reportFinish(taskId, _, TaskFinishReason.Failed)
    ),
    destination,
    Vector.empty
  ))
  def addTask(
      taskId: ID,
      source: () => IN,
      destination: OUT
  ): Option[(TaskInfo[ID, OUT], PoolState[ID, IN, OUT])] =
    if (stopRequest.isDefined) None
    else
      Some(
        if (running < concurrency || concurrency == 0) {
          val time = System.currentTimeMillis
          (
            TaskInfo(taskId, 0, time, TaskCurrentState.Running()),
            copy(
              running = running + 1,
              tasks = tasks + createWorker(taskId, time, source(), destination)
            )
          )
        } else {
          (
            TaskInfo(taskId, 0, 0, TaskCurrentState.Scheduled()),
            copy(
              queue =
                queue :+ PoolState.QueuedTask(taskId, source, destination),
              tasks = tasks + (taskId -> TaskRunState.Scheduled(destination))
            )
          )
        }
      )
  def getTask(
      taskId: ID,
      onTaskInfo: TaskInfo[ID, OUT] => Unit
  ): (Boolean, Option[PoolState[ID, IN, OUT]]) = {
    val task = tasks.get(taskId)
    val newState = task.flatMap {
      case TaskRunState.Scheduled(_) =>
        onTaskInfo(TaskInfo(taskId, 0, 0, TaskCurrentState.Scheduled()))
        None
      case r @ TaskRunState.Running(_, _, _, _) =>
        r.worker.currentCount(reportCount(taskId, _))
        val newTaskState = r.copy[OUT](countingRequests =
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
      case f @ TaskRunState.Finished(_, _, _, _, _) =>
        onTaskInfo(
          TaskInfo(
            taskId,
            f.linesProcessed,
            f.runningSince,
            TaskCurrentState.Finished(f.finishedAt, f.destination, f.reason)
          )
        )
        None
    }
    (task.isDefined, newState)
  }
  def listTasks(): Seq[TaskShortInfo[ID]] = tasks.toSeq.map {
    case (taskId, state) =>
      state match {
        case TaskRunState.Scheduled(_) =>
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
            tasks + (taskId -> r.copy[OUT](
              countingRequests = r.countingRequests :+ reply
            ))
          )
        )
      case TaskRunState.Scheduled(destination) =>
        reply(0)
        Some(
          copy(
            queue = queue.filterNot(_.taskId == taskId),
            tasks = tasks + (taskId -> TaskRunState
              .Finished[OUT](0, 0, 0, destination, TaskFinishReason.Cancelled))
          )
        )
      case TaskRunState.Finished(_, _, linesProcessed, _, _) =>
        reply(linesProcessed)
        None
    }
    (task.isDefined, newState)
  }
  def cancelAll(reportStop: () => Unit): PoolState[ID, IN, OUT] =
    copy(
      stopRequest = if (running <= 0) {
        reportStop()
        None
      } else {
        Some(reportStop)
      },
      queue = Vector.empty,
      tasks = tasks.map { case (taskId, state) =>
        (
          taskId,
          state match {
            case r @ TaskRunState.Running(_, worker, _, _) =>
              worker.cancel(reportFinish(taskId, _, TaskFinishReason.Cancelled))
              r
            case TaskRunState.Scheduled(destination) =>
              TaskRunState.Finished(
                0,
                0,
                0,
                destination,
                TaskFinishReason.Cancelled
              )
            case f @ TaskRunState.Finished(_, _, _, _, _) => f
          }
        )
      }
    )
  def finishTask(
      taskId: ID,
      totalCount: Long,
      reason: TaskFinishReason
  ): Option[(Option[ID], PoolState[ID, IN, OUT])] =
    tasks.get(taskId) flatMap {
      case r @ TaskRunState.Running(_, _, _, _) =>
        val newTaskState = TaskRunState.Finished[OUT](
          r.runningSince,
          System.currentTimeMillis,
          totalCount,
          r.destination,
          reason
        )
        r.countingRequests.foreach(_(totalCount))
        val newTasks = tasks + (taskId -> newTaskState)
        val queueHead = queue.headOption
        val newState = queueHead match {
          case None =>
            if (running <= 1) stopRequest.foreach(_())
            copy(running = running - 1, tasks = newTasks)
          case Some(task) =>
            copy(
              queue = queue.drop(1),
              tasks = newTasks + createWorker(
                task.taskId,
                System.currentTimeMillis,
                task.source(),
                task.destination
              )
            )
        }
        r.destination.finalize(reason)
        Some((queueHead.map(_.taskId), newState))
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
