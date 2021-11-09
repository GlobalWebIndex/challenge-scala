package com.example

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.stream._
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl.{FileIO, Flow, Source}
import akka.util.ByteString
import com.example.TaskActor._

import java.nio.file.Paths
import java.time.Duration
import scala.language.postfixOps

/**
 * @author Petros Siatos
 */
trait TaskToFileFlow {
  val flow: Flow[Task, IOResult, NotUsed]
}

class CsvToJsonDownloader(implicit system: ActorSystem[_]) extends TaskToFileFlow {
  implicit val mat: Materializer = Materializer(system)

  val PARALELLISM: Int = system.settings.config.getInt("downloader.parallelism")
  val OUTPUT_FOLDER: String = system.settings.config.getString("downloader.output-folder")
  val DELAY_SECONDS: Duration = system.settings.config.getDuration("downloader.delay")

  val flow: Flow[Task, IOResult, NotUsed] = Flow[Task].mapAsyncUnordered(PARALELLISM) { task =>
    val request = Get(task.csvUri)
    //.withHeaders(Authorization(BasicHttpCredentials(username = KAGGLE_USERNAME, password = KAGGLE_API_KEY)))
    val source = Source.single(request)
    val filePath = Paths.get(s"$OUTPUT_FOLDER/${task.id}.json")

    import spray.json.DefaultJsonProtocol._

    import scala.concurrent.duration._

    source
      .delay(DELAY_SECONDS.toSeconds seconds)
      .mapAsyncUnordered(1)(Http()(system).singleRequest(_))
      .flatMapConcat(extractEntityData)
      .via(CsvParsing.lineScanner())
      .via(CsvToMap.toMapAsStrings())
      .map(toJson)
      .map(_.compactPrint)
      .intersperse("[", ",\n", "]")
      .map(ByteString(_))
      .withAttributes(ActorAttributes.dispatcher("csv-download-dispatcher"))
      .runWith(FileIO.toPath(filePath))
  }

}
