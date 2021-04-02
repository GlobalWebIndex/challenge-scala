package pl.datart.csvtojson.model

sealed abstract class CancellationResult {
  def asString: String
}

object CancellationResult {
  final case object Canceled extends CancellationResult {
    def asString: String = "Canceled"
  }

  final case class NotCanceled(reason: String) extends CancellationResult {
    def asString: String = s"NotCanceled, reason: $reason"
  }
}
