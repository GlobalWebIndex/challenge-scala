package conversion

import scala.util.Random
import java.io.File

class ConversionWorker(url: String, result: File, onDone: Long => Unit) {
  def cancel(onCancel: () => Unit): Unit = onCancel()
  def currentCount(onCount: Long => Unit): Unit = onCount(Random.nextLong())
}
