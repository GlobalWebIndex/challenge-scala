package pool.dependencies

import com.typesafe.config.{Config => TSConfig}

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

final case class Config(concurrency: Int, timeout: FiniteDuration)
object Config {
  def fromConf(configuraton: TSConfig): Config =
    Config(
      concurrency = configuraton.getInt("concurrency"),
      timeout = Duration(configuraton.getLong("timeout"), "ms")
    )
}
