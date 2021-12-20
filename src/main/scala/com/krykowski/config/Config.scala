package com.krykowski.config

import com.typesafe.config.ConfigFactory

object Config {

  private val config = ConfigFactory.load()

  val appConfig = AppConfig(
    config.getString("application.host"),
    config.getInt("application.port")
  )
}
