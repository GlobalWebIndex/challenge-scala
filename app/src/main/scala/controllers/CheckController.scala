package controllers

import org.slf4j.Logger

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class CheckController(log: Logger) {
  def check: Route = {
    log.debug("System runs")
    complete("running")
  }
}
