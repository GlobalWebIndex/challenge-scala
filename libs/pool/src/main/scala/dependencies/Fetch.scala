package pool.dependencies

import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.Source

/** Customizable utilities for dealing with sources */
trait Fetch[IN, ITEM] {

  /** Creates a stream of items from the source address
    *
    * @param url
    *   Source address
    * @return
    *   Stream of items
    */
  def make(url: IN)(implicit as: ActorSystem[_]): Source[ITEM, _]
}
