package conversion

import play.api.Configuration

import java.nio.file.Path
import java.nio.file.Paths
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

final case class ConversionConfig(
    concurrency: Int,
    timeout: FiniteDuration,
    resultDirectory: Path
)
object ConversionConfig {
  def fromConf(config: Configuration): ConversionConfig =
    ConversionConfig(
      concurrency = config.get[Int]("conversion.concurrency"),
      timeout = Duration(config.get[Long]("conversion.timeout"), "ms"),
      resultDirectory =
        Paths.get(config.get[String]("conversion.resultDirectory"))
    )
}
