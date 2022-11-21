package com.github.maenolis.http

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class HttpClientRequestService(implicit
    system: ActorSystem[_],
    ec: ExecutionContext
) {

  def getRemoteFile(url: String): Future[HttpResponse] = {
    Http()
      .singleRequest(HttpRequest(uri = url))
  }

}
