package com.krykowski

package object model {

  case class Task(id: Option[Long], status: Status = Scheduled, jsonFileUri: String)
  case class CsvFileLocation(name: String, uri: String)

  abstract sealed class Status(val value: String)

  case object Scheduled extends Status("Scheduled")

  case object Running extends Status("Running")

  case object Done extends Status("Done")

  case object Failed extends Status("Failed")

  case object Canceled extends Status("Canceled")


  object Status {
    private def values = Set(Scheduled, Running, Done, Failed, Canceled)

    def unsafeFromString(value: String): Status = {
      values.find(_.value == value).get
    }
  }

  case object TaskNotFoundError
}
