package cz.vlasec.gwi.csvimport

import io.circe._
import io.circe.parser._
import io.circe.syntax._

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.{ContentTypeRange, HttpEntity}
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}

import scala.concurrent.Future

// from https://gist.github.com/mattroberts297/c531a4e9e525d6a18cbf8889ab5f3dec
/**
 * To use circe for json marshalling and unmarshalling:
 *
 * import CirceSupport._
 * import io.circe.generic.auto._
 */
object CirceSupport {
  private def jsonContentTypes: List[ContentTypeRange] =
    List(`application/json`)

  implicit final def unmarshaller[A: Decoder]: FromEntityUnmarshaller[A] = {
    Unmarshaller.stringUnmarshaller
      .forContentTypes(jsonContentTypes: _*)
      .flatMap { ctx => mat => json =>
        decode[A](json).fold(Future.failed, Future.successful)
      }
  }

  implicit final def marshaller[A: Encoder]: ToEntityMarshaller[A] = {
    Marshaller.withFixedContentType(`application/json`) { a =>
      HttpEntity(`application/json`, a.asJson.noSpaces)
    }
  }
}