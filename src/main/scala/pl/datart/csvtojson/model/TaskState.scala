package pl.datart.csvtojson.model

sealed abstract class TaskState {
  def asString: String
}

object TaskState {
  final case object Scheduled extends TaskState {
    def asString: String = "SCHEDULED"
  }

  final case object Running extends TaskState {
    def asString: String = "RUNNING"
  }

  final case object Done extends TaskState {
    def asString: String = "DONE"
  }

  final case object Failed extends TaskState {
    def asString: String = "FAILED"
  }

  final case object Canceled extends TaskState {
    def asString: String = "CANCELED"
  }
}
