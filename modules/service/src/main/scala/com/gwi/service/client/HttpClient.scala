package com.gwi.service.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.{Inject, Singleton}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HttpClient @Inject() ()(implicit actorSystem: ActorSystem, executionContext: ExecutionContext) {

  def getCsvFile(url: String): Source[ByteString, Future[Any]] =
    Source
      .futureSource(Http().singleRequest(HttpRequest(uri = url)).map(_.entity.dataBytes))
}
