package pl.marboz.gwi.application.algebra

import cats.effect.{IO, Ref}
import munit.CatsEffectSuite
import org.http4s.client.{Client, JavaNetClientBuilder}
import org.http4s.{Method, Request, Response, Status}
import org.http4s.implicits.http4sLiteralsSyntax
import pl.marboz.gwi.api.http.TaskRoutes
import pl.marboz.gwi.application.model.Task

import java.util.UUID

/**
 * @author <a href="mailto:marboz85@gmail.com">Marcin Bozek</a>
 * Date: 20.05.2023 23:19
 */
//TODO:
class TaskAlgebraSpec extends CatsEffectSuite {

  test("TaskAlgebra returns status code 200") {
    assertIO(retTaskAlgebra.map(_.status), Status.Ok)
  }

  test("TaskAlgebra returns hello world message") {
    assertIO(retTaskAlgebra.flatMap(_.as[String]), "{\"status\":\"DONE\"}")
  }

  private[this] val retTaskAlgebra: IO[Response[IO]] = {
    val getHW = Request[IO](Method.GET, uri"task/2de418c0-dd74-49c2-8390-c42556c661e9")
    val httpClient: Client[IO] = JavaNetClientBuilder[IO].create
    val taskAlgebra = TaskAlgebra.impl[IO](Ref[IO].of(Map.empty[UUID, Task]).unsafeRunSync(), httpClient)
    TaskRoutes.routes(taskAlgebra).orNotFound(getHW)
  }

}
