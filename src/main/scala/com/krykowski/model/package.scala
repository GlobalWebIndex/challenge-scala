package com.krykowski

package object model {

  case class Task(id: Option[Long], status: Status = Scheduled, jsonFileUri: String)
  case class CsvFileLocation(name: String, uri: String)

  abstract sealed class Status(val value: String)

  case object Scheduled extends Status("SCHEDULED")

  case object Running extends Status("RUNNING")

  case object Done extends Status("DONE")

  case object Failed extends Status("FAILED")

  case object Canceled extends Status("CANCELED")


  object Status {
    private def values = Set(Scheduled, Running, Done, Failed, Canceled)

    def unsafeFromString(value: String): Status = {
      values.find(_.value == value).get
    }
  }

  case object TaskNotFoundError
}
