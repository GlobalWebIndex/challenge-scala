package cz.vlasec.gwi.csvimport.service

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import cz.vlasec.gwi.csvimport.service.CsvService.CsvServiceCommand
import cz.vlasec.gwi.csvimport.service.CsvTask.{CsvDetail, CsvTaskCommand, StatusReport}
import cz.vlasec.gwi.csvimport.service.Demo.{TestService, TestTask}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object DummyMain {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "my-system")
    implicit val scheduler: Scheduler = system.scheduler
    implicit val timeout: Timeout = 100.millis

    val demoRef = system.systemActorOf(Demo(), "demo")

    val serviceRef = system.systemActorOf[CsvServiceCommand](CsvService(), "csv-service")
    demoRef ! TestService(serviceRef)

    val taskRef = system.systemActorOf[CsvTaskCommand](CsvTask(69, CsvDetail("http://example.com/some.csv")), "task")
    demoRef ! TestTask(taskRef) // Waits in mailbox while previous command is being processed.
  }
}

object Demo {
  sealed trait TestCommand
  case class TestTask(ref: ActorRef[CsvTaskCommand]) extends TestCommand
  case class TestService(ref: ActorRef[CsvServiceCommand]) extends TestCommand

  def apply()(implicit timeout: Timeout, scheduler: Scheduler): Behavior[TestCommand] = idle()

  def idle()(implicit timeout: Timeout, scheduler: Scheduler): Behavior[TestCommand] = Behaviors.receiveMessage {
    case TestTask(taskRef) =>
      System.out.println(Await.result(taskRef.ask(ref => CsvTask.Run(ref)), timeout.duration))
      Thread.sleep(100)
      taskRef ! CsvTask.ProcessLines(420)
      taskRef ! CsvTask.Finish("https://example.com/something.json", 42)
      taskRef ! CsvTask.Cancel
      awaitStatus(taskRef) // should return the same result as above, because Cancel doesn't work
      Behaviors.same
    case TestService(serviceRef) =>
      val url: CsvUrl = "http://example.com/some.csv"
      val firstId = Await.result(serviceRef.ask(ref => CsvService.ConvertCsv(url, ref)), timeout.duration)
      Thread.sleep(1500)
      serviceRef.ask(ref => CsvService.ConvertCsv(url, ref))
      serviceRef.ask(ref => CsvService.ConvertCsv(url, ref))
      serviceRef.ask(ref => CsvService.ConvertCsv(url, ref))
      Thread.sleep(4000)
      System.out.println(Await.result(serviceRef.ask(ref => CsvService.GetStatus(firstId, ref)), timeout.duration))
      Thread.sleep(8000)
      Behaviors.same
  }

  private def awaitStatus(taskRef: ActorRef[CsvTaskCommand])(implicit timeout: Timeout, scheduler: Scheduler): Unit =
    System.out.println(Await.result(taskRef.ask(ref => StatusReport(ref)), 10.millis))
}
