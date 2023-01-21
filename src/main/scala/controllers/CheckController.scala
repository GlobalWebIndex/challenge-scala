package controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.slf4j.Logger

class CheckController(log: Logger) {
  def check: Route = {
    log.debug("System runs")
    complete("running")
  }
}
