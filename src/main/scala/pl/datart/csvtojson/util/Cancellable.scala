package pl.datart.csvtojson.util

trait Cancellable[F[_]] {
  def cancel: F[Unit]
}
