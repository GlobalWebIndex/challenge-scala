package com.gwi.database.model.persistent.dao

import akka.actor.ActorSystem
import akka.stream.alpakka.slick.scaladsl.SlickSession

trait SQLRepo {

  implicit val actorSystem: ActorSystem

  implicit val session: SlickSession = {
    val session: SlickSession = SlickSession.forConfig("slick-postgres")
    actorSystem.registerOnTermination(session.close())
    session
  }
}
