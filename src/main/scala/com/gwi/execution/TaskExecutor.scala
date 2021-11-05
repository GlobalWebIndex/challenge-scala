package com.gwi.execution

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
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
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class TaskExecutor(taskRepository: TaskRepository, taskStorage: TaskStorage, parallelTaskCount: Int)(implicit
    system: ActorSystem,
    ec: ExecutionContext
) extends RequestBuilding {

  private val logger = Logging.getLogger(system, this.getClass)
  private val killSwitchByRunningTask = TrieMap[UUID, KillSwitch]()

  val bufferSize = 10

  private val queue: SourceQueueWithComplete[UUID] = Source
    .queue[UUID](bufferSize, OverflowStrategy.backpressure)
    .mapAsync(parallelTaskCount)(processTask)
    .toMat(Sink.ignore)(Keep.left)
    .run()

  def processTask(taskId: UUID): Future[Task] = taskRepository.getTask(taskId) flatMap {
    case Some(task) if Task.canBeProcessed(task) =>
      val taskStarted = task.copy(state = TaskState.Running, startedAt = Some(Instant.now()))
      logger.info(s"Task started: [$taskStarted]")

      val (result, killSwitch) = Source
        .future(taskRepository.updateTask(taskStarted))
        .delay(10.seconds)
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
        .andThen { case Success(t) =>
          killSwitchByRunningTask.remove(task.id)
          taskRepository.updateTask(t)
          logger.info(s"Task done: [$t]")
        }

    case Some(task) =>
      logger.info(s"Task [${task.id}] is not in processable state, task state: [${task.state}] - skipping")
      Future.successful(task)
    case _ =>
      val msg = s"Task [$taskId] does not exists"
      logger.error(msg)
      Future.failed(new Exception(msg))
  }

  def downloadCsvSource(uri: Uri): Source[ByteString, NotUsed] =
    Source.future(Http().singleRequest(HttpRequest(uri = uri))).flatMapConcat(_.entity.dataBytes)

  def cancelTaskExecution(taskId: UUID): Boolean =
    killSwitchByRunningTask.get(taskId) match {
      case Some(ks) =>
        logger.info(s"Running task [$taskId] was canceled")
        ks.shutdown()
        true
      case _ =>
        logger.info(s"Task [$taskId] is not running")
        false
    }

  def enqueueTask(taskId: UUID): Future[Boolean] = queue.offer(taskId).map {
    case QueueOfferResult.Enqueued =>
      logger.info(s"Task enqueued: [$taskId]")
      true
    case QueueOfferResult.Dropped =>
      logger.info(s"Task dropped: [$taskId]")
      false
    case QueueOfferResult.Failure(ex) =>
      logger.info(s"Offer for task $taskId failed ${ex.getMessage}")
      false
    case QueueOfferResult.QueueClosed =>
      logger.info("Source Queue closed")
      false
  }

}
