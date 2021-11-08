package com.gwi.execution

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{KillSwitch, KillSwitches, OverflowStrategy, QueueOfferResult}
import akka.util.ByteString
import com.gwi.api.TaskState
import com.gwi.repository.TaskRepository
import com.gwi.storage.TaskStorage
import io.circe.syntax.EncoderOps

import java.time.Instant
import java.util.UUID
import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

class TaskExecutorImpl(taskRepository: TaskRepository, taskStorage: TaskStorage, parallelTaskCount: Int)(implicit
    system: ActorSystem,
    ec: ExecutionContext
) extends TaskExecutor {

  private val logger = Logging.getLogger(system, this.getClass)
  private val killSwitchByRunningTask = TrieMap[UUID, KillSwitch]()

  val bufferSize = 10

  private val queue: SourceQueueWithComplete[UUID] = Source
    .queue[UUID](bufferSize, OverflowStrategy.backpressure)
    .mapAsync(parallelTaskCount)(processTask)
    .toMat(Sink.ignore)(Keep.left)
    .run()

  private def processTask(taskId: UUID): Future[Task] = taskRepository.getTask(taskId) flatMap {
    case Some(task) if Task.canBeProcessed(task) =>
      val taskStarted = task.copy(state = TaskState.Running, startedAt = Some(Instant.now()))
      logger.info(s"Task started: [$taskStarted]")

      val (result, killSwitch) = Source
        .future(taskRepository.updateTask(taskStarted))
        .flatMapConcat(_ => downloadCsvSource(task.csvUri))
        .via(CsvParsing.lineScanner())
        .via(CsvToMap.toMapAsStrings())
        .map(l => ByteString(l.asJson.noSpaces))
        .alsoToMat(taskStorage.jsonSink(task.id))(Keep.right)
        .scanAsync(0L)((acc, _) => taskRepository.setLinesProcessed(task.id, acc + 1))
        .viaMat(KillSwitches.single)(Keep.both)
        .toMat(Sink.ignore)(Keep.left)
        .run()

      killSwitchByRunningTask.addOne(task.id, killSwitch)

      result
        .flatMap(_ => taskRepository.getTask(task.id))
        .recoverWith { case ex =>
          logger.error(s"Task processing failed with message: ${ex.getMessage}")
          taskRepository.getTask(task.id).map(_.map(_.copy(state = TaskState.Failed, endedAt = Some(Instant.now()))))
        }
        .map {
          // Stopped by killswitch is already in database
          case Some(t) if t.state == TaskState.Canceled => t
          // Failed is handled in previous step
          case Some(t) if t.state == TaskState.Failed => t
          case Some(t) => t.copy(state = TaskState.Done, endedAt = Some(Instant.now()))
        }
        .flatMap { t =>
          killSwitchByRunningTask.remove(task.id)

          taskRepository.updateTask(t).map(_ => t).recover { case ex =>
            logger.error(s"Task update failed with message: ${ex.getMessage}")
            t
          }
        }
        .andThen(t => logger.info(s"Task done: [$t]"))

    case Some(task) =>
      logger.info(s"Task [${task.id}] is not in processable state, task state: [${task.state}] - skipping")
      Future.successful(task)
    case _ =>
      val msg = s"Task [$taskId] does not exists"
      logger.error(msg)
      Future.failed(new Exception(msg))
  }

  private def downloadCsvSource(uri: Uri): Source[ByteString, NotUsed] =
    Source.future(Http().singleRequest(HttpRequest(uri = uri))).flatMapConcat(_.entity.dataBytes)

  override def cancelTaskExecution(taskId: UUID): Boolean =
    killSwitchByRunningTask.get(taskId) match {
      case Some(ks) =>
        logger.info(s"Running task [$taskId] was canceled")
        ks.shutdown()
        true
      case _ =>
        logger.info(s"Task [$taskId] is not running")
        false
    }

  override def enqueueTask(taskId: UUID): Future[Boolean] = queue.offer(taskId).flatMap {
    case QueueOfferResult.Enqueued =>
      logger.info(s"Task enqueued: [$taskId]")
      Future.successful(true)
    case QueueOfferResult.Dropped =>
      val msg = s"Task dropped: [$taskId]"
      logger.warning(msg)
      Future.failed(new Exception(msg))
    case QueueOfferResult.Failure(ex) =>
      val msg = s"Offer for task $taskId failed ${ex.getMessage}"
      logger.error(msg)
      Future.failed(new Exception(msg))
    case QueueOfferResult.QueueClosed =>
      val msg = "Source Queue closed"
      logger.warning(msg)
      Future.failed(new Exception(msg))
  }

}
