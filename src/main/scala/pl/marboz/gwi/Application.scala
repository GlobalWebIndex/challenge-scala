package pl.marboz.gwi

import cats.effect._
import com.comcast.ip4s.IpLiteralSyntax
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import pl.marboz.gwi.api.http.TaskRoutes
import pl.marboz.gwi.application.algebra.TaskAlgebra
import pl.marboz.gwi.application.model.Task

import java.util.UUID

/** @author
  *   <a href="mailto:marboz85@gmail.com">Marcin Bozek</a> Date: 19.05.2023 22:53
  */
object Application extends IOApp.Simple {

  val ip = ipv4"0.0.0.0"
  val port = port"8080"
  def run[F[_]: Async]: F[Nothing] = {
    for {
      taskState <- Resource.liftK(Ref[F].of(Map.empty[UUID, Task]))

      client <- EmberClientBuilder.default[F].build

      taskAlg = TaskAlgebra.impl[F](taskState, client)

      httpApp = (
        TaskRoutes.routes[F](taskAlg)
      ).orNotFound

      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      _ <-
        EmberServerBuilder
          .default[F]
          .withHost(ip)
          .withPort(port)
          .withHttpApp(finalHttpApp)
          .build
    } yield ()
  }.useForever

  val run = run[IO]
}
