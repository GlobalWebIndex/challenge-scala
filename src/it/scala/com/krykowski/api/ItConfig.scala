package com.krykowski.api

import com.krykowski.config.AppConfig
import com.typesafe.config.ConfigFactory

object ItConfig {
  private val config = ConfigFactory.load("it.conf")

  val appConfig = AppConfig(
    config.getString("application.host"),
    config.getInt("application.port")
  )
}
