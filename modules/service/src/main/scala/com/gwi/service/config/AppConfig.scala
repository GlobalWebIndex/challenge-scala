package com.gwi.service.config

import com.google.inject.Singleton
import com.typesafe.config.ConfigFactory

case class ServerConfig(ip: String, port: Int)
case class BackPressure(bufferSize: Int, queueSize: Int)

@Singleton
class AppConfig() {

  private val config = ConfigFactory.load().getConfig("gwi")

  val server: ServerConfig = ServerConfig(config.getString("server.ip"), config.getInt("server.port"))
  val backPressure: BackPressure =
    BackPressure(config.getInt("backpressure.buffer-size"), config.getInt("backpressure.queue-size"))
  val concurrencyFactor: Int = config.getInt("concurrency-factor")

}
