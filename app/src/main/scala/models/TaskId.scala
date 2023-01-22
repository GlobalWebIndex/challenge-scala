package models

import io.circe.Encoder

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.server.PathMatcher1
import akka.http.scaladsl.server.PathMatchers

final case class TaskId(id: String)
object TaskId {
  val Matcher: PathMatcher1[TaskId] = PathMatchers.Segment.map(TaskId(_))
  implicit val taskIdEncoder: Encoder[TaskId] = Encoder[String].contramap(_.id)
  implicit val taskIdToResponse: ToResponseMarshaller[TaskId] =
    implicitly[ToResponseMarshaller[String]].compose(_.id)
}
