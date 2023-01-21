package controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class CheckController {
  def check: Route = complete("running")
}
