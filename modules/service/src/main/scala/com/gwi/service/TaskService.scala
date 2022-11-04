package com.gwi.service

import akka.NotUsed
import akka.actor.{ActorSystem, Cancellable}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{BoundedSourceQueue, KillSwitch, KillSwitches, OverflowStrategy, QueueOfferResult}
import com.google.inject.{Inject, Singleton}
import com.gwi.database.model.memory.TaskState.TaskState
import com.gwi.database.model.memory.dao.TaskRepository
import com.gwi.database.model.memory.{Task, TaskState}
import com.gwi.database.model.persistent.JsonLine
import com.gwi.database.model.persistent.dao.{DoneTaskFromJsonLines, JsonLineRepository}
import com.gwi.service.dto.TaskCanceledResult
import com.gwi.service.client.HttpClient
import com.gwi.service.config.AppConfig
import com.gwi.service.dto.TaskCanceledResult.TaskCanceledResult
import com.gwi.service.dto.{TaskCompletion, TaskDto}
import com.gwi.service.exception.TaskCanceledException
import com.gwi.service.flow.TaskFlowService
import com.typesafe.scalalogging.LazyLogging
import spray.json.JsValue

import java.util.UUID
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaskService @Inject() (
  taskRepository: TaskRepository,
  jsonLineRepository: JsonLineRepository,
  client: HttpClient,
  taskFlowService: TaskFlowService,
  config: AppConfig
)(implicit val actorSystem: ActorSystem, executionContext: ExecutionContext)
    extends LazyLogging {

  private val queue: BoundedSourceQueue[(UUID, String)] = createQueue()
  private val tasksKillSwitchMap: mutable.Map[UUID, KillSwitch] = mutable.Map[UUID, KillSwitch]()

  private final val FINAL_STATES = Seq(TaskState.DONE, TaskState.CANCELED, TaskState.FAILED)

  var isReady: Boolean = false
  loadDoneTasks()

  private def loadDoneTasks(): Unit = {
    val sink = Sink.foreach[DoneTaskFromJsonLines](task =>
      taskRepository.upsert(
        Task(
          task.taskId,
          task.linesProcessed,
          task.totalProcessingTime,
          TaskState.DONE,
          Some(createJsonLineRetrieveUrl(task.taskId))
        )
      )
    )

    jsonLineRepository.findAllTasks().runWith(sink).onComplete(_ => isReady = true)
  }

  def cancelTask(taskId: UUID): TaskCanceledResult = {
    val taskOpt = taskRepository
      .get(taskId)

    val abortedOpt = taskOpt
      .filter(task => task.state == TaskState.RUNNING || task.state == TaskState.SCHEDULED)
      .map(task =>
        tasksKillSwitchMap
          .get(taskId)
          .map(_.abort(new TaskCanceledException("task canceled")))
          .getOrElse(taskRepository.upsert(task.copy(state = TaskState.CANCELED)))
      )

    if (taskOpt.isEmpty) {
      TaskCanceledResult.NOT_FOUND
    } else if (taskOpt.nonEmpty && abortedOpt.isEmpty) {
      TaskCanceledResult.NOT_CANCELABLE_STATE
    } else {
      TaskCanceledResult.SUCCESS
    }
  }

  def getAllTasks(): Source[TaskDto, NotUsed] = Source(taskRepository.getAll())
    .filter(task =>
      (task.totalProcessingTime == 0 && task.linesProcessed == 0) || (task.totalProcessingTime > 0 && task.linesProcessed > 0)
    )
    .map(mapTaskToDto)

  def getTaskInfo(taskId: UUID): Source[Option[TaskDto], Cancellable] = Source
    .tick(0.seconds, 2.seconds, ())
    .map(_ => taskRepository.get(taskId))
    .takeWhile(
      task => {
        task.nonEmpty && !task
          .map(_.state)
          .exists(state => FINAL_STATES.contains(state))
      },
      inclusive = true
    )
    .map(_.map(mapTaskToDto))

  def createJsonLineRetrieveUrl(taskId: UUID): String =
    s"http://${config.server.ip}:${config.server.port}/task/result/$taskId"

  def getJsonLines(taskId: UUID): Source[String, NotUsed] = jsonLineRepository
    .getJsonLines(taskId)
    .map(js => {
      logger.info(s"$js")
      js.line
    })

  def createTask(url: String): UUID = {
    val taskId = UUID.randomUUID()

    val task = Task(taskId, 0, 0, TaskState.SCHEDULED, None)
    taskRepository.upsert(task)
    queue.offer((taskId, url)) match {
      case QueueOfferResult.Enqueued =>
        logger.info(s"Task $taskId was scheduled")
      case QueueOfferResult.Dropped =>
        logger.error(s"Buffer full, could not schedule task $taskId")
        taskRepository.upsert(task.copy(state = TaskState.FAILED))
      case QueueOfferResult.Failure(ex) =>
        logger.error(s"Error while scheduling task $taskId. Message: ${ex.getMessage}, $ex")
        taskRepository.upsert(task.copy(state = TaskState.FAILED))
      case QueueOfferResult.QueueClosed =>
        logger.error(s"Buffer closed. Cannot schedule task $taskId")
        taskRepository.upsert(task.copy(state = TaskState.FAILED))
    }
    taskId
  }

  private def persistLineFlow(taskId: UUID): Flow[(JsValue, Long), Unit, NotUsed] =
    Flow[(JsValue, Long)].map { case (line, startTime) =>
      val processTime = System.currentTimeMillis() - startTime
      jsonLineRepository.create(JsonLine(taskId, processTime, line.toString()))
      taskRepository
        .get(taskId)
        .foreach(task =>
          taskRepository.upsert(
            task.copy(
              linesProcessed = task.linesProcessed + 1,
              totalProcessingTime = task.totalProcessingTime + processTime
            )
          )
        )
    }

  private def markTaskComplete(taskId: UUID): Unit = {
    logger.info(s"Task $taskId was completed")
    taskRepository
      .get(taskId)
      .foreach(task => taskRepository.upsert(task.copy(state = TaskState.DONE)))
    jsonLineRepository.markJsonLinesCompleted(taskId)
  }

  private def markTaskFailed(taskId: UUID, taskState: TaskState): Future[Int] = {
    logger.info(s"Task $taskId was $taskState")
    taskRepository
      .get(taskId)
      .foreach(task => taskRepository.upsert(task.copy(state = taskState)))
    jsonLineRepository.markJsonLinesCompleted(taskId)
  }

  private def mapTaskToDto(task: Task): TaskDto =
    (task.linesProcessed, task.totalProcessingTime) match {
      case (0, 0) =>
        TaskDto(
          task.id,
          0,
          0,
          task.state.toString,
          Option.when(task.state == TaskState.DONE)(createJsonLineRetrieveUrl(task.id))
        )
      case (lines, time) =>
        val averageLinesProcessed =
          lines / time
        TaskDto(
          task.id,
          lines,
          averageLinesProcessed,
          task.state.toString,
          Option.when(task.state == TaskState.DONE)(createJsonLineRetrieveUrl(task.id))
        )
    }

  private def createQueue(): BoundedSourceQueue[(UUID, String)] =
    Source
      .queue[(UUID, String)](config.backPressure.queueSize)
      .mapAsync(config.concurrencyFactor) { case (taskId, url) =>
        taskRepository
          .get(taskId)
          .filter(_.state != TaskState.CANCELED)
          .map(task => {
            taskRepository.upsert(task.copy(state = TaskState.RUNNING))
            logger.info(s"Task $taskId started")
            val (killSwitch, result) =
              client
                .getCsvFile(url)
                .buffer(config.backPressure.bufferSize, OverflowStrategy.backpressure)
                .async
                .via(taskFlowService.createConsumeCsvFlowFlow())
                .buffer(config.backPressure.bufferSize, OverflowStrategy.backpressure)
                .async
                .via(persistLineFlow(taskId))
                .buffer(config.backPressure.bufferSize, OverflowStrategy.backpressure)
                .async
                .viaMat(KillSwitches.single)(Keep.right)
                .toMat(Sink.ignore)(Keep.both)
                .run()

            tasksKillSwitchMap.update(taskId, killSwitch)
            result.map(_ => TaskCompletion(taskId, wasCanceled = false, hasFailed = false)).recover {
              case exception if exception.isInstanceOf[TaskCanceledException] =>
                logger.warn(s"Task $taskId was canceled")
                TaskCompletion(taskId, wasCanceled = true, hasFailed = false)
              case exception =>
                logger.error(s"Task $taskId failed. ${exception.getMessage}")
                TaskCompletion(taskId, wasCanceled = false, hasFailed = true)
            }
          })
          .getOrElse(Future(TaskCompletion(taskId, wasCanceled = true, hasFailed = false)))
      }
      .to(
        Sink.foreach(taskCompletion =>
          if (taskCompletion.hasFailed) {
            markTaskFailed(taskCompletion.taskId, TaskState.FAILED)
          } else if (taskCompletion.wasCanceled) {
            markTaskFailed(taskCompletion.taskId, TaskState.CANCELED)
          } else {
            markTaskComplete(taskCompletion.taskId)
          }
        )
      )
      .run()
}
