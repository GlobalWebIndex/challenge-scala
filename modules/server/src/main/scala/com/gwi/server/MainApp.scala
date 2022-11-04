package com.gwi.server

import com.google.inject.Guice

object MainApp {

  def main(args: Array[String]): Unit = {
    val injector = Guice.createInjector(new AppModule())
    val server = injector.getInstance(classOf[HttpServer])
    server.startServer()
  }
}
