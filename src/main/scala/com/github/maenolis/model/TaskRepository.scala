package com.github.maenolis.model

import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag
import slick.sql.{FixedSqlAction, FixedSqlStreamingAction, SqlAction}

object TaskRepository {

  implicit val statusEnumMapper =
    MappedColumnType.base[TaskStatus.TaskStatusEnum, String](
      e => e.toString,
      s => TaskStatus.withName(s)
    )

  class Tasks(tag: Tag) extends Table[Task](tag, "tasks") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def uri = column[String]("uri")

    def status = column[TaskStatus.TaskStatusEnum]("status")

    def timeStarted = column[Option[Long]]("time_started")

    def timeEnded = column[Option[Long]]("time_ended")

    def * =
      (id, uri, status, timeStarted, timeEnded) <> (Task.tupled, Task.unapply)
  }

  private val tasksQuery = TableQuery[Tasks]

  private val tasksInsertQuery =
    (tasksQuery returning tasksQuery.map(_.id)) into ((task, id) =>
      Task(id, task.uri, task.status)
    )

  def getAllTasks(): FixedSqlStreamingAction[Seq[Task], Task, Effect.Read] =
    tasksQuery.result

  def getTasksByStatus(
      taskStatus: TaskStatus.TaskStatusEnum
  ): FixedSqlStreamingAction[Seq[Task], Task, Effect.Read] =
    tasksQuery.filter(_.status === taskStatus).result

  def taskByIdQuery(id: Long): SqlAction[Option[Task], NoStream, Effect.Read] =
    tasksQuery.filter(_.id === id).result.headOption

  def insertTask(
      taskDto: TaskDto
  ): FixedSqlAction[Task, NoStream, Effect.Write] =
    tasksInsertQuery += Task(0, taskDto.uri, TaskStatus.Scheduled)

  def updateStatus(
      id: Long,
      newStatus: TaskStatus.TaskStatusEnum
  ): FixedSqlAction[Int, NoStream, Effect.Write] = {
    val q = for { t <- tasksQuery if t.id === id } yield t.status
    q.update(newStatus)
  }

  def updateTimeStarted(
      id: Long,
      newTimeStarted: Long
  ): FixedSqlAction[Int, NoStream, Effect.Write] = {
    val q = for { t <- tasksQuery if t.id === id } yield t.timeStarted
    q.update(Some(newTimeStarted))
  }

  def updateTimeEnded(
      id: Long,
      newTimeEnded: Long
  ): FixedSqlAction[Int, NoStream, Effect.Write] = {
    val q = for { t <- tasksQuery if t.id === id } yield t.timeEnded
    q.update(Some(newTimeEnded))
  }

}
