package gwi

import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.model.HttpRequest
import akka.stream._
import akka.stream.alpakka.csv.scaladsl._
import akka.stream.scaladsl._
import akka.util.ByteString
import io.circe.Printer
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util._

object Worker {
  type Graph = RunnableGraph[(KillSwitch, Future[TaskStats])]
  type Factory = (RunningTask, ActorRef[Response]) => Behavior[Command]

  // Command messages
  sealed trait Command

  case object Cancel extends Command
  case object Stop extends Command

  private case class StreamFinished(stats: TaskStats) extends Command
  private case class StreamFailed(cause: Throwable) extends Command

  // Response messages
  sealed trait Response

  final case class ReportStats(id: TaskId, stats: TaskStats) extends Response
  final case class WorkerFinished(id: TaskId, stats: TaskStats) extends Response
  final case class WorkerFailed(id: TaskId, cause: Throwable) extends Response

  def apply(graph: Graph, id: TaskId, respondTo: ActorRef[Response]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      implicit val system: ActorSystem[_] = context.system

      val (killSwitch, f) = graph.run()
      context.pipeToSelf(f) {
        case Success(stats) => StreamFinished(stats)
        case Failure(cause) => StreamFailed(cause)
      }

      Behaviors.receive[Command] {
        case (_, Cancel) =>
          killSwitch.shutdown()
          Behaviors.same

        case (_, Stop) =>
          Behaviors.stopped

        case (_, StreamFinished(stats)) =>
          respondTo ! WorkerFinished(id, stats)
          Behaviors.stopped

        case (_, StreamFailed(cause)) =>
          respondTo ! WorkerFailed(id, cause)
          Behaviors.stopped
      }
    }

  def factory(system: ActorSystem[_], storage: Storage): Factory = (task, respondTo) => {
    import system.executionContext

    val streamingSupport = EntityStreamingSupport.json()

    val fetchCsv: Future[Source[ByteString, _]] = {
      val raw = Http(system)
        .singleRequest(HttpRequest(uri = task.source))
        .map(_.entity.dataBytes)

      if (task.source.path.endsWith(".zip")) raw.map(_.via(Compression.deflate))
      else raw
    }

    val sinkToFile: Sink[ByteString, _] = Flow[ByteString]
      .via(streamingSupport.framingRenderer)
      .to(storage.sink(task.id))

    val reportToManager: Sink[Long, _] = Flow[Long]
      .conflateWithSeed((_, 1L)) { case ((_, acc), linesProcessed) => (linesProcessed, acc + 1) }
      .zip(Source.tick(1.second, 1.second, NotUsed))
      .map({ case ((linesProcessed, linesProcessedPerSec), _) => TaskStats(linesProcessed, linesProcessedPerSec) })
      .to(Sink.foreach(stats => respondTo ! ReportStats(task.id, stats)))

    val graph: Graph = Source
      // Record current millis when the stream starts
      .lazySource(() => Source.empty.mapMaterializedValue(_ => System.currentTimeMillis()))
      .concatMat(Source.futureSource(fetchCsv))(Keep.left)
      // Parse CSV content, transform lines to Map[String, String] and convert each line to JSON
      .via(CsvParsing.lineScanner())
      .via(CsvToMap.toMapAsStrings(StandardCharsets.UTF_8))
      .map(line => ByteString(Printer.noSpaces.printToByteBuffer(line.asJson)))
      // Stream JSON to file
      .alsoTo(sinkToFile)
      // Calculate number of processed lines
      .zipWithIndex
      .map(_._2)
      // Report intermediate stats to the manager
      .wireTap(reportToManager)
      // Add a kill switch
      .viaMat(KillSwitches.single)(Keep.both)
      // Collect overall stats
      .toMat(Sink.last)(Keep.both)
      .mapMaterializedValue({ case ((startF, killSwitch), linesProcessedF) =>
        val statsF = for {
          start <- startF
          linesProcessed <- linesProcessedF
        } yield TaskStats.overall(linesProcessed, System.currentTimeMillis() - start)
        (killSwitch, statsF)
      })

    apply(graph, task.id, respondTo)
  }
}
