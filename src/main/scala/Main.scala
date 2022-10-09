import cats.effect._
import cats.effect.std._
import cats.implicits._
import fs2.Stream
import fs2.io.file.{Files, Path}
import org.http4s.server.Router
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s._
import scala.concurrent.duration._
import scala.collection.concurrent

object Main extends IOApp{
  import TaskModel._
  import Workers._
  import Routes._
  override def run(args: List[String]): IO[ExitCode] = {
    val taskMap: concurrent.Map[String, Task] = concurrent.TrieMap()
    val runnableServer = for {
      _ <- Files[IO].createDirectories(Path("jsons"))
      schedulerQueue <- Queue.unbounded[IO, String]
      workerQueue <- Queue.bounded[IO, String](2)
      _ <- scheduler(taskMap, schedulerQueue, workerQueue).start
      _ <- (1 to 2).toList.map(workerId => worker(taskMap, workerId, workerQueue)).parTraverse_(_.start)
      _ <- Stream.awakeEvery[IO](1.second)
        .evalTap(_ => IO(for (elem <- taskMap.values) {
          if (elem.linesPerSecond.get() > 0) {
            elem.avgLinesProcess.set(elem.linesPerSecond.get)
            elem.linesPerSecond.set(0)
          }
        }))
        .compile
        .drain.start
      routes = taskRoutes(taskMap, schedulerQueue)
      apis = Router(
        "/" -> routes,
      ).orNotFound
      server <- EmberServerBuilder.default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(apis)
        .build.use(_ => IO.never)
    } yield server

    runnableServer.as(ExitCode.Success)
  }
}
