package com.gwi.karelsk

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import com.opencsv.CSVReader
import spray.json.DefaultJsonProtocol._
import spray.json._

import java.io.InputStreamReader
import java.net.{URI, URL}
import java.nio.file.{Files, Path}
import java.time.Clock
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

object TaskWorker {

  sealed trait Command
  private final case object Continue extends Command

  final case class ProgressReport(id: Task.Id, progress: Task.Progress)

  def apply(id: Task.Id, from: URI, result: Path, reportTo: ActorRef[ProgressReport]): Behavior[Nothing] = {
    val behavior = Behaviors setup[Command] { setupContext =>
      type Line = ((Array[String], Int), (Long, Boolean))

      setupContext.log.debug("Starting the CSV to JSON conversion from {} to {}", from, result)
      val csvLinesWithIndex = csvLinesFrom(from.toURL).zipWithIndex
      val out = Files.newBufferedWriter(result)

      @tailrec
      def convert(lines: LazyList[Line], progress: (Int, Long), separate: Boolean): (LazyList[Line], Task.Progress) =
        if (lines.isEmpty) (lines, progress) else {
          val ((csv, i), (t, report)) = lines.head
          if (separate) out.write(",\n  ")
          out.write(csv.toJson.compactPrint)
          Thread.sleep(10)
          if (report) (lines.tail, (i + 1, t))
          else convert(lines.tail, (i + 1, t), separate = true)
        }

      def worker(lines: LazyList[Line], progress: Task.Progress, separate: Boolean = true): Behaviors.Receive[Command] =
        Behaviors receive[Command] { case (context, Continue) =>
          if (lines.isEmpty) {
            out.write("\n]")
            out.close()
            reportTo ! ProgressReport(id, progress)
            Behaviors.stopped
          } else {
            val (ls, p) = convert(lines, progress, separate)
            reportTo ! ProgressReport(id, progress)
            context.self ! Continue
            worker(ls, p)
          }
        }

      val lines = csvLinesWithIndex zip punctuate(1000)
      out.write("[\n  ")
      setupContext.self ! Continue

      worker(lines, (0,0), separate = false) receiveSignal {
        case (_, PostStop) =>
          setupContext.log.debug("Worker for task {} stopped", id)
          out.close()
          Behaviors.same
        case (_, s) =>
          setupContext.log.debug("Worker for task {} received signal {}", id, s)
          Behaviors.same
      }
    }

    behavior.narrow
  }

  def csvLinesFrom(url: URL): LazyList[Array[String]] = {
    val csvReader = new CSVReader(new InputStreamReader(url.openStream()))
    LazyList.from(csvReader.iterator().asScala)
  }

  def punctuate(period: Long, clock: Clock = Clock.systemUTC): LazyList[(Long, Boolean)] = {
    val startTime = clock.millis()
    def loop(prev: Long): LazyList[(Long, Boolean)] = {
      val elapsed = clock.millis() - startTime
      val quotient = elapsed / period
      (elapsed, quotient > prev) #:: loop(quotient)
    }
    loop(-1)
  }

  def repeatedCountUp(count: Int): LazyList[Int] = LazyList.continually(0 until count).flatten

  def elapsed(clock: Clock = Clock.systemUTC): LazyList[Long] = {
    val startTime = clock.millis()
    def loop: LazyList[Long] = (clock.millis() - startTime) #:: loop
    loop
  }
}
