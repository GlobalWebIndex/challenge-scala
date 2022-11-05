package com.gwi.database.model.persistent.dao

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.scaladsl.{Sink, Source}
import com.google.inject.{Inject, Singleton}
import com.gwi.database.model.persistent.JsonLine
import slick.jdbc.{ResultSetConcurrency, ResultSetType}
import slick.sql.FixedSqlAction

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.Future

@Singleton
class JsonLineRepository @Inject() (implicit val actorSystem: ActorSystem) extends SQLRepo {

  private final val STREAM_PAGE_SIZE: Int = 128

  import session.profile.api._

  class JsonLineTable(tag: Tag) extends Table[JsonLine](tag, "json_line") {
    val id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    val taskId = column[UUID]("task_id")

    val processTime = column[Long]("process_time")

    val line = column[String]("line")

    val timeCreated = column[LocalDateTime]("time_created")

    val isCompleted = column[Boolean]("is_completed")

    val taskIdIndex = index("task_id_idx", taskId)

    def * = (taskId, processTime, line, timeCreated, isCompleted, id.?).mapTo[JsonLine]
  }

  lazy val jsonLine = TableQuery[JsonLineTable]

  import slick.migration.api._
  implicit val dialect: PostgresDialect = new PostgresDialect

  val migration =
    TableMigration(jsonLine).create
      .addColumns(_.id, _.taskId, _.processTime, _.line, _.timeCreated, _.isCompleted)
      .addIndexes(_.taskIdIndex)
  session.db.run(migration())

  lazy val insertJsonLine: session.profile.ReturningInsertActionComposer[JsonLine, JsonLine] =
    jsonLine returning jsonLine

  //def batchCreate(jsonLines: Seq[JsonLine]): DBIO[Int] = insertJsonLine ++= jsonLines

  def sinkCreate: Sink[JsonLine, Future[Done]] =
    Slick.sink[JsonLine](parallelism = 10, (line: JsonLine) => (jsonLine += line).transactionally)

  def create(jsonLine: JsonLine) =
    session.db.run(createJsonLineQuery(jsonLine).transactionally)

  def createJsonLineQuery(jsonLine: JsonLine) =
    insertJsonLine += jsonLine

  def markJsonLinesCompleted(taskId: UUID) =
    session.db.run(jsonLine.filter(_.taskId === taskId).map(_.isCompleted).update(true))

  def deleteFailedLines(taskId: UUID) =
    session.db.run(jsonLine.filter(_.taskId === taskId).delete)

  def getJsonLines(taskId: UUID): Source[JsonLine, NotUsed] = Slick
    .source(
      jsonLine
        .filter(_.taskId === taskId)
        //.filter(_.isCompleted)
        .sortBy(_.timeCreated.desc)
        .result
        .withStatementParameters(
          rsType = ResultSetType.ForwardOnly,
          rsConcurrency = ResultSetConcurrency.ReadOnly,
          fetchSize = STREAM_PAGE_SIZE
        )
        .transactionally
    )

  def findAllTasks(): Source[DoneTaskFromJsonLines, NotUsed] = Slick
    .source(
      jsonLine
        .filter(_.isCompleted)
        .groupBy(_.taskId)
        .map { case (taskId, jsonLine) =>
          (taskId, jsonLine.size, jsonLine.map(_.processTime).sum)
        }
        .result
        .withStatementParameters(
          rsType = ResultSetType.ForwardOnly,
          rsConcurrency = ResultSetConcurrency.ReadOnly,
          fetchSize = STREAM_PAGE_SIZE
        )
        .transactionally
    )
    .map { case (taskId, linesProcessed, totalProcessingTime) =>
      DoneTaskFromJsonLines(taskId, linesProcessed, totalProcessingTime.getOrElse(0))
    }
}

case class DoneTaskFromJsonLines(taskId: UUID, linesProcessed: Int, totalProcessingTime: Long)
