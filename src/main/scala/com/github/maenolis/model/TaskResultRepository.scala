package com.github.maenolis.model

import slick.dbio.Effect
import slick.jdbc.{PostgresProfile, ResultSetConcurrency, ResultSetType}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag
import slick.sql.FixedSqlAction
import spray.json.{JsString, JsValue}

object TaskResultRepository {

  implicit val jsonMapper = MappedColumnType.base[JsValue, String](
    jsValue => jsValue.toString,
    jsString => JsString(jsString)
  )

  class TaskResults(tag: Tag) extends Table[TaskResult](tag, "task_results") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def csvContent = column[JsValue]("csv_content")

    def taskId = column[Long]("task_id")

    def * = (id, csvContent, taskId) <> (TaskResult.tupled, TaskResult.unapply)
  }

  val tasksResultsQuery = TableQuery[TaskResults]

  def taskResultCountByTaskIdQuery(
      taskId: Long
  ): FixedSqlAction[Int, PostgresProfile.api.NoStream, Effect.Read] =
    tasksResultsQuery.filter(_.taskId === taskId).length.result

  def insertTaskResult(
      taskResultDto: TaskResultDto
  ): FixedSqlAction[Int, NoStream, Effect.Write] =
    tasksResultsQuery += TaskResult(
      0,
      taskResultDto.csvContent,
      taskResultDto.taskId
    )

  def getTaskJsonStream(taskId: Long): DBIOAction[Seq[TaskResult], Streaming[
    TaskResult
  ], Effect.Read with Effect.Transactional] = tasksResultsQuery
    .filter(_.taskId === taskId)
    .result
    .withStatementParameters(
      rsType = ResultSetType.ForwardOnly,
      rsConcurrency = ResultSetConcurrency.ReadOnly,
      fetchSize = 2
    )
    .transactionally
}
