package pool.dependencies

import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.Source

trait Fetch[IN, ITEM] {
  def make(url: IN)(implicit as: ActorSystem[_]): Source[ITEM, _]
}
