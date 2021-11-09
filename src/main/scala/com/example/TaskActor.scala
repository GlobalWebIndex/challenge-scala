package com.example

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.OK
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveEnumerationCodec
import spray.json.{JsValue, JsonWriter}

import scala.collection.immutable
import scala.collection.immutable.Queue
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * @author Petros Siatos
 */
object TaskActor {

  def jsonUri(taskId: Int): String = s"localhost:8080/files/$taskId"

  final case object TaskCanceledException extends Exception

  sealed trait TaskStatus {
    override def toString: String = this.getClass.getSimpleName
  }

  object SCHEDULED extends TaskStatus

  object RUNNING extends TaskStatus

  object DONE extends TaskStatus

  object FAILED extends TaskStatus

  object CANCELED extends TaskStatus

  object TaskStatus {
    implicit val modeCodec: Codec[TaskStatus] = deriveEnumerationCodec[TaskStatus]
  }


  final case class Task(id: Int, csvUri: String, status: TaskStatus, jsonUri: Option[String] = None)

  final case class ListTasksResponse(tasks: immutable.Seq[Task])
  final case class TaskActorResponse(message: String)


  sealed trait TaskMessage

  final case class CreateTask(csvUri: String, replyTo: ActorRef[Task]) extends TaskMessage
  final case class ListTasks(replyTo: ActorRef[ListTasksResponse]) extends TaskMessage
  final case class CancelTask(id: Int, replyTo: ActorRef[TaskActorResponse]) extends TaskMessage

  // Internal Messages
  final case class TaskDone(id: Int) extends TaskMessage
  final case class TaskFailed(id: Int, exception: Throwable) extends TaskMessage

  final case class TaskActorContext(runningTasks: Map[Int, UniqueKillSwitch],
                                    taskQueue: Queue[Task],
                                    allTasks: Map[Int, Task])


  def extractEntityData(response: HttpResponse): Source[ByteString, _] =
    response match {
      case HttpResponse(OK, _, entity, _) => entity.dataBytes
      case notOkResponse =>
        Source.failed(new RuntimeException(s"illegal response $notOkResponse"))
    }


  def toJson(map: Map[String, String])(implicit jsWriter: JsonWriter[Map[String, String]]): JsValue = jsWriter.write(map)

  def apply(taskToFileFlow: TaskToFileFlow): Behavior[TaskMessage] = Behaviors.setup(ctx => {
    ctx.log.info("Starting")
    taskActorBehavior(ctx, TaskActorContext(Map.empty, Queue.empty, Map.empty))(taskToFileFlow)
  })

  private def taskActorBehavior(ctx: ActorContext[TaskMessage], taskCtx: TaskActorContext)(implicit taskToFileFlow: TaskToFileFlow): Behavior[TaskMessage] = {
    implicit val mat: Materializer = Materializer(ctx.system)

    val PARALELLISM: Int = ctx.system.settings.config.getInt("downloader.parallelism")
    val taskFlow: TaskToFileFlow= new CsvToJsonDownloader()(ctx.system)

    def runTask(task: Task): UniqueKillSwitch = {
      Source(List(task))
        .via(taskFlow.flow)
        .viaMat(KillSwitches.single)(Keep.both)
        .to(Sink.onComplete {
          case Success(_) => ctx.self ! TaskDone(task.id)
          case Failure(ex) => ctx.self ! TaskFailed(task.id, ex)
        })
        .run()
        // Return KillSwitch
        ._2
    }

    /**
     * Runs next task if queue is not empty and MAX_CAPACITY of running tasks is not reached
     * Otherwise returns the same taskContext
     * @param taskContext the current status of all tasks
     * @return updated taskContext
     */
    def runNextTask(taskContext: TaskActorContext): TaskActorContext = {
      taskContext.taskQueue match {
        case next_task +: taskQueueTail =>
          if (taskContext.runningTasks.size < PARALELLISM) {
            ctx.log.info(s"Running Task ${next_task.id}")
            val killSwitch = runTask(next_task)
            val updatedRunningTasks = taskContext.runningTasks + (next_task.id -> killSwitch)
            val updatedAllTasks = taskContext.allTasks + (next_task.id -> next_task.copy(status = RUNNING))
            TaskActorContext(updatedRunningTasks, taskQueueTail, updatedAllTasks)
          } else {
            taskContext
          }
        case _ =>
          ctx.log.debug("Queue is empty")
          taskContext
      }
    }

    Behaviors.receiveMessage {
      case ListTasks(replyTo) =>
        replyTo ! ListTasksResponse(taskCtx.allTasks.values.toSeq)
        Behaviors.same
      case CreateTask(csvUri, replyTo) =>
        val taskId = taskCtx.allTasks.size + 1
        val task = Task(taskId, csvUri, SCHEDULED)
        ctx.log.info(s"Scheduling task ${task.id} ${task.csvUri}")

        // Add new scheduled task
        var updatedTaskCtx = taskCtx.copy(
          taskQueue = taskCtx.taskQueue :+ task,
          allTasks = taskCtx.allTasks + (task.id -> task)
        )
        // Run immediately if possible
        updatedTaskCtx = runNextTask(updatedTaskCtx)
        // In case task started running immediately and has changed status
        replyTo ! updatedTaskCtx.allTasks(taskId)
        taskActorBehavior(ctx, updatedTaskCtx)
      case CancelTask(id, replyTo) =>
        ctx.log.info(s"Canceling Task with $id")

        val updatedTaskCtx: TaskActorContext = taskCtx.runningTasks.get(id) match {
          // Kill stream if task was running
          case Some(killSwitch) =>
            ctx.log.info(s"Task $id was Running and Killed")
            killSwitch.abort(TaskCanceledException)
            val afterCancelTaskCtx = taskCtx.copy(
              runningTasks = taskCtx.runningTasks - id,
              allTasks = taskCtx.allTasks + (id -> taskCtx.allTasks(id).copy(status = CANCELED)))
            // Run next
            replyTo ! TaskActorResponse(s"Task $id was canceled")
            runNextTask(afterCancelTaskCtx)
          case None =>
            taskCtx.allTasks.get(id) match {
              // No task with given id exists
              case None =>
                val message = s"Task $id does not exists"
                ctx.log.info(message)
                replyTo ! TaskActorResponse(message)
                taskCtx
              // Cancel scheduled task.
              case Some(_) =>
                val message = s"Task $id was not running"
                ctx.log.info(message)
                replyTo ! TaskActorResponse(message)
                taskCtx.copy(
                    allTasks = taskCtx.allTasks + (id -> taskCtx.allTasks(id).copy(status = CANCELED)))
            }
        }
        taskActorBehavior(ctx, updatedTaskCtx)
      case TaskDone(id) =>
        ctx.log.info(s"Task $id Completed Successfully")

        // Remove Failed task, mark as done
        var updatedTaskCtx = taskCtx.copy(
          runningTasks = taskCtx.runningTasks - id,
          allTasks = taskCtx.allTasks + (id -> taskCtx.allTasks(id).copy(status = DONE, jsonUri = Some(jsonUri(id))))
        )
        // Run next
        updatedTaskCtx = runNextTask(updatedTaskCtx)
        taskActorBehavior(ctx, updatedTaskCtx)

      case TaskFailed(id, TaskCanceledException) =>
        ctx.log.warn(s"Task $id Canceled")
        Behaviors.same

      case TaskFailed(id, ex) =>
        ctx.log.error(s"Task $id Failed ${ex.getMessage}")

        // Remove Failed task, mark as failed
        var updatedTaskCtx = taskCtx.copy(
          runningTasks = taskCtx.runningTasks - id,
          allTasks = taskCtx.allTasks + (id -> taskCtx.allTasks(id).copy(status = FAILED))
        )
        // Run next
        updatedTaskCtx = runNextTask(updatedTaskCtx)
        taskActorBehavior(ctx, updatedTaskCtx)
    }
  }
}
