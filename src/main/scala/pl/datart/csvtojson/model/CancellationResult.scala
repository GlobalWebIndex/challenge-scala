package pl.datart.csvtojson.model

sealed abstract class CancellationResult

object CancellationResult {
  final case object Canceled                   extends CancellationResult
  final case class NotCanceled(reason: String) extends CancellationResult
}
