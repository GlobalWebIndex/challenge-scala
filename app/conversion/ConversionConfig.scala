package conversion

import play.api.Configuration

import java.nio.file.Path
import java.nio.file.Paths

final case class ConversionConfig(concurrency: Int, resultDirectory: Path)
object ConversionConfig {
  def fromConf(config: Configuration): ConversionConfig =
    ConversionConfig(
      concurrency = config.get[Int]("conversion.concurrency"),
      resultDirectory =
        Paths.get(config.get[String]("conversion.resultDirectory"))
    )
}
