package conversion

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Source
import akka.util.ByteString

trait ConversionSource {
  def make(url: Uri)(implicit as: ActorSystem[_]): Source[ByteString, _]
}
object HttpConversionSource extends ConversionSource {
  def make(url: Uri)(implicit as: ActorSystem[_]): Source[ByteString, _] =
    Source
      .futureSource(
        Http()
          .singleRequest(HttpRequest(HttpMethods.GET, url))
          .map(response =>
            if (response.entity.contentType.mediaType == MediaTypes.`text/csv`)
              response.entity.dataBytes
            else
              Source.failed(
                new Exception(
                  s"Incorrect content-type: ${response.entity.contentType}"
                )
              )
          )(as.executionContext)
      )
}
