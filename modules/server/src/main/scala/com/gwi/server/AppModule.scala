package com.gwi.server

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext

class AppModule extends AbstractModule {

  override def configure(): Unit = requestInjection(this)

  @Provides
  @Singleton
  def system(): ActorSystem = {
    implicit val actorSystem: ActorSystem = ActorSystem("csv-to-json-http", ConfigFactory.load())
    actorSystem
  }

  @Provides
  @Singleton
  def executionContext(implicit actorSystem: ActorSystem): ExecutionContext =
    actorSystem.dispatcher

  @Provides
  @Singleton
  def materializer(implicit actorSystem: ActorSystem): Materializer = Materializer(actorSystem)
}
