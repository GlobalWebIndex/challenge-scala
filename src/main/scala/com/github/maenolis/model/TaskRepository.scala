package com.github.maenolis.model

import slick.dbio.Effect
import slick.lifted.Tag
import slick.jdbc.PostgresProfile.api._
import slick.sql.FixedSqlAction

object TaskRepository {

  implicit val statusEnumMapper = MappedColumnType.base[TaskStatus.TaskStatusEnum, String](
    e => e.toString,
    s => TaskStatus.withName(s)
  )

  class Tasks(tag: Tag) extends Table[Task](tag, "tasks") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def uri = column[String]("uri")

    def status = column[TaskStatus.TaskStatusEnum]("status")

    def * = (id, uri, status) <> (Task.tupled, Task.unapply)
  }

  val tasksQuery = TableQuery[Tasks]

  def taskByIdQuery(id: Long): Query[Tasks, Task, Seq] = tasksQuery.filter(_.id === id)

  def insertTask(taskDto: TaskDto): FixedSqlAction[Int, NoStream, Effect.Write] = tasksQuery += Task(0, taskDto.uri, TaskStatus.Scheduled)

  def updateStatus(id: Long, newStatus: TaskStatus.TaskStatusEnum): FixedSqlAction[Int, NoStream, Effect.Write] = {
    val q = for { t <- tasksQuery if t.id === id } yield t.status
    q.update(newStatus)
  }

}


