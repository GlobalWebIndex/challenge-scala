package pool.dependencies

import scala.concurrent.duration.FiniteDuration

trait Cfg {
  def concurrency: Int
  def timeout: FiniteDuration
}
