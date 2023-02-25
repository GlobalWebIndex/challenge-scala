package cz.vlasec.gwi.csvimport.service

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, Scheduler}
import akka.util.Timeout
import cz.vlasec.gwi.csvimport.service.CsvService.WorkerIdle
import cz.vlasec.gwi.csvimport.service.CsvTask.{Finish, ProcessLines}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random

private[service] object CsvWorker {

  sealed trait CsvWorkerCommand
  final case class ConvertCsv(taskRef: TaskRef, serviceRef: ServiceRef) extends CsvWorkerCommand

  def apply()(implicit scheduler: Scheduler): Behavior[CsvWorkerCommand] = idle()

  private def idle()(implicit scheduler: Scheduler): Behavior[CsvWorkerCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case ConvertCsv(taskRef, serviceRef) =>
        implicit def timeout: Timeout = 100.millis
        val detail = Await.result(taskRef.ask(ref => CsvTask.Run(ref)), timeout.duration)
        context.log.info(s"Processing CSV at ${detail.url}")
        simulateWork(taskRef)
        serviceRef ! WorkerIdle(context.self)
        Behaviors.same
      case x =>
        context.log.warn(s"Invalid command $x"); Behaviors.same
    }
  }

  private def simulateWork(taskRef: TaskRef): Unit = {
    // TODO replace this with actual stream processing the CSV
    for (i <- 1 to 5) {
      Thread.sleep(ccaThousand())
      taskRef ! ProcessLines(ccaThousand())
    }
    Thread.sleep(ccaThousand() - 400)
    taskRef ! Finish(s"http://example.com/${taskRef.path}.json", ccaThousand() - 400)
  }

  private def ccaThousand(): Long = Random.nextInt(500)+800 // a simulation of lines being read
}
