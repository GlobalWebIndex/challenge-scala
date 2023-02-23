package cz.vlasec.gwi.csvimport.manager

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import cz.vlasec.gwi.csvimport.manager.CsvTask.{CsvTaskCommand, StatusReport}
import cz.vlasec.gwi.csvimport.manager.TaskTester.TestWithRef

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt

object DummyMain extends App {
  implicit val system: ActorSystem[_] = ActorSystem(Behaviors.empty, "my-system")
  implicit val executionContext: ExecutionContext = system.executionContext
  implicit val scheduler: Scheduler = system.scheduler
  implicit val timeout: Timeout = 100.millis

  val taskRef = system.systemActorOf[CsvTaskCommand](CsvTask(69), "task")
  val tester = system.systemActorOf(TaskTester(), "taskTester")
  tester ! TestWithRef(taskRef)
}

object TaskTester {
  sealed trait TestCommand
  case class TestWithRef(ref: ActorRef[CsvTaskCommand]) extends TestCommand

  def apply()(implicit timeout: Timeout, scheduler: Scheduler): Behavior[TestCommand] = idle()

  def idle()(implicit timeout: Timeout, scheduler: Scheduler): Behavior[TestCommand] = Behaviors.receiveMessage {
    case TestWithRef(taskRef) =>
      awaitStatus(taskRef)
      taskRef ! CsvTask.Run
      awaitStatus(taskRef)
      taskRef ! CsvTask.LinesProcessed(420)
      awaitStatus(taskRef)
      taskRef ! CsvTask.Finish("https://example.com/something.json")
      awaitStatus(taskRef)
      taskRef ! CsvTask.Cancel
      awaitStatus(taskRef) // should return the same result as above, because Cancel doesn't work
      Behaviors.same
  }

  private def awaitStatus(taskRef: ActorRef[CsvTaskCommand])(implicit timeout: Timeout, scheduler: Scheduler): Unit =
    System.out.println(Await.result(taskRef.ask(ref => StatusReport(ref)), 10.millis))
}
