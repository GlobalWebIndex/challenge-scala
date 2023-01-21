package conversion

import pool.dependencies.Fetch

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, MediaTypes, Uri}
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.json.Json

object HttpConversion extends Fetch[Uri, ByteString] {
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
      .via(CsvParsing.lineScanner())
      .via(CsvToMap.toMap())
      .map(Json.toJson(_))
      .map(Json.stringify(_))
      .map(ByteString(_))

}
