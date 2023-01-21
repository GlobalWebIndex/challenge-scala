package pool

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Source
import akka.util.ByteString

trait Fetch {
  def make(url: Uri)(implicit as: ActorSystem[_]): Source[ByteString, _]
}
