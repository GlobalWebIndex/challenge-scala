package pool.dependencies

import com.typesafe.config.{Config => TSConfig}

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

/** Configurable parameters of the task pool
  *
  * @param concurrency
  *   Number of the tasks running simultaneously; value 0 means there is no
  *   limit
  * @param timeout
  *   Time limit for getting information from the pool
  */
final case class Config(concurrency: Int, timeout: FiniteDuration)

/** Shortcuts for generating [[Config]] values */
object Config {

  /** Generate [[Config]] from application configuration
    *
    * @param configuraton
    *   Application configuration
    * @return
    *   Generated [[Config]]
    */
  def fromConf(configuraton: TSConfig): Config =
    Config(
      concurrency = configuraton.getInt("concurrency"),
      timeout = Duration(configuraton.getLong("timeout"), "ms")
    )
}
