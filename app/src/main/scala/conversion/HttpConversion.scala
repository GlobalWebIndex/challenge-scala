package conversion

import io.circe.Encoder._
import io.circe.syntax._
import pool.dependencies.Fetch

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.Uri
import akka.stream.alpakka.csv.scaladsl.CsvParsing
import akka.stream.alpakka.csv.scaladsl.CsvToMap
import akka.stream.scaladsl.Source
import akka.util.ByteString

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
      .via(CsvToMap.toMapAsStrings())
      .map(_.asJson.noSpaces)
      .map(ByteString(_))

}
