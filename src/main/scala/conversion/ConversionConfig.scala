package conversion

import com.typesafe.config.Config
import pool.dependencies.Cfg

import java.nio.file.Path
import java.nio.file.Paths
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

final case class ConversionConfig(
    concurrency: Int,
    timeout: FiniteDuration,
    resultDirectory: Path
) extends Cfg
object ConversionConfig {
  def fromConf(config: Config): ConversionConfig =
    ConversionConfig(
      concurrency = config.getInt("concurrency"),
      timeout = Duration(config.getLong("timeout"), "ms"),
      resultDirectory = Paths.get(config.getString("resultDirectory"))
    )
}
