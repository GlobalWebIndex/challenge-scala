package com.github.maenolis.service

import akka.NotUsed
import akka.actor.Cancellable
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.OK
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.alpakka.slick.scaladsl._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{KillSwitches, OverflowStrategy, UniqueKillSwitch}
import akka.util.ByteString
import com.github.maenolis.csv.CsvHelper.toJson
import com.github.maenolis.flow.CanceledFlowException
import com.github.maenolis.http.HttpClientRequestService
import com.github.maenolis.model._
import slick.basic.DatabasePublisher
import slick.jdbc.JdbcBackend.Database
import spray.json.DefaultJsonProtocol

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal

class TaskService(httpRequests: HttpClientRequestService, db: Database)(implicit
    ec: ExecutionContext,
    system: ActorSystem[_]
) extends DefaultJsonProtocol {

  import slick.jdbc.PostgresProfile.api._

  implicit val slickSessionCreatedForDbAndProfile: SlickSession = SlickSession
    .forDbAndProfile(db, slick.jdbc.PostgresProfile.api.slickProfile)

  val killSwitches = scala.collection.mutable.Map.empty[Long, UniqueKillSwitch]

  val taskQueue = Source
    .queue[Task](1000, OverflowStrategy.backpressure)
    .mapAsync(2)(task => {
      val (killSwitch, last) = Source
        .future(
          db.run(
            DBIO.seq(
              TaskRepository.updateStatus(task.id, TaskStatus.Running),
              TaskRepository
                .updateTimeStarted(task.id, System.currentTimeMillis())
            )
          ).flatMap(_ => {
            httpRequests.getRemoteFile(task.uri)
          })
        )
        .buffer(10000, OverflowStrategy.backpressure)
        .flatMapConcat(extractEntityData)
        .via(CsvParsing.lineScanner())
        .via(CsvToMap.toMapAsStrings())
        .map(toJson)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(
          Slick.sink(csvJson =>
            TaskResultRepository.insertTaskResult(
              TaskResultDto(csvJson, task.id)
            )
          )
        )(Keep.both)
        .run()

      killSwitches.put(task.id, killSwitch)

      last
        .map(_ => task.id -> TaskStatus.Done)
        .recover({
          case _: CanceledFlowException => task.id -> TaskStatus.Canceled
          case NonFatal(_) => task.id -> TaskStatus.Failed
        })
    })
    .toMat(Sink.foreach(idStatus => {
      db.run(
        DBIO.seq(
          TaskRepository.updateStatus(idStatus._1, idStatus._2),
          TaskRepository
            .updateTimeEnded(idStatus._1, System.currentTimeMillis())
        )
      )
    }))(Keep.both)
    .run()

  private def extractEntityData(response: HttpResponse): Source[ByteString, _] =
    response match {
      case HttpResponse(OK, _, entity, _) => entity.dataBytes
      case notOkResponse =>
        Source.failed(new RuntimeException(s"illegal response $notOkResponse"))
    }

  def getTasks(): Future[Seq[Task]] = db.run(TaskRepository.getAllTasks())

  def getTaskDetails(id: Long): Future[Option[TaskDetailsDto]] = {

    def avgCalculation(task: Task, count: Int): Option[Float] = {
      Option()
        .filter(_ => task.status != TaskStatus.Scheduled)
        .flatMap(_ => task.timeStarted)
        .map(timeStarted =>
          task.timeEnded.getOrElse(System.currentTimeMillis()) - timeStarted
        )
        .map(taskDuration => (count.toFloat / taskDuration) * 1000)
    }

    for (
      taskOpt <- db.run(TaskRepository.taskByIdQuery(id));
      resultsCount <- db.run(
        TaskResultRepository.taskResultCountByTaskIdQuery(id)
      )
    )
      yield taskOpt.map(task =>
        TaskDetailsDto(
          Some(resultsCount),
          avgCalculation(task, resultsCount),
          task.status.toString,
          s"http://localhost:8080/json/${task.id}"
        )
      )

  }

  def getTaskJson(taskId: Long): DatabasePublisher[TaskResult] = {
    db.stream(TaskResultRepository.getTaskJsonStream(taskId))
  }

  def insert(taskDto: TaskDto): Future[Task] = {
    db
      .run(TaskRepository.insertTask(taskDto))
      .map(task => {
        taskQueue._1.offer(task)
        task
      })
  }

  def cancel(id: Long): Future[Unit] = db
    .run(TaskRepository.taskByIdQuery(id))
    .map { case Some(task) =>
      task
    }
    .filter(task => TaskStatus.isCancelable(task.status))
    .map(_ => {
      killSwitches
        .get(id)
        .foreach(_.abort(new CanceledFlowException))
    })

}
