package cz.vlasec.gwi.csvimport.task

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, Scheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.util.Timeout
import cz.vlasec.gwi.csvimport.task.Task.ProcessLines

import scala.concurrent.Await
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.util.Random

/**
 * The actor that represents the actual processing power allocated to convert CSV to JSON.
 * When idle, worker reports itself to its overseer and awaits a task to process.
 * When in busy state, worker monitors the stream that processes the data and takes metrics.
 */
private[task] object Worker {

  sealed trait WorkerCommand
  final case class ProcessTask(taskRef: TaskRef) extends WorkerCommand
  final case object CancelTask extends WorkerCommand
  // The future of these two is uncertain after the introduction of the actual stream.
  private case object FinishTask extends WorkerCommand
  private case class FakeWork(ticksLeft: Int) extends WorkerCommand


  def apply(overseerRef: OverseerRef): Behavior[WorkerCommand] = idle(overseerRef)

  private def idle(overseerRef: OverseerRef): Behavior[WorkerCommand] = Behaviors.setup { context =>
      overseerRef ! Overseer.IdleWorker(context.self)
      Behaviors.receiveMessage {
        case ProcessTask(taskRef) =>
          busy(taskRef, overseerRef)
        case x =>
          context.log.warn(s"Invalid command $x"); Behaviors.same
      }
  }

  private def busy(taskRef: TaskRef, overseerRef: OverseerRef): Behavior[WorkerCommand] = Behaviors.setup { context =>
    implicit def timeout: Timeout = 100.millis
    implicit val scheduler: Scheduler = context.system.scheduler
    val detail = Await.result(taskRef.ask(ref => Task.Run(context.self, ref)), timeout.duration)
    context.log.info(s"Processing CSV at ${detail.url}")
    context.self ! FakeWork(6)
    Behaviors.withTimers { timers =>
      Behaviors.receiveMessage {
        case CancelTask =>
          timers.cancelAll()// stops fake work
          idle(overseerRef)
        case FinishTask =>
          taskRef ! Task.Finish(s"http://example.com/converted/${taskRef.path.name}.json", ccaThousand() - 400)
          idle(overseerRef)
        case FakeWork(ticksLeft) =>
          // TODO replace the mocks with real processing
          if (ticksLeft <= 0) {
            timers.startSingleTimer(FinishTask, ccaThousand().millis)
          } else {
            timers.startSingleTimer(FakeWork(ticksLeft - 1), ccaThousand().millis)
          }
          taskRef ! ProcessLines(ccaThousand())
          Behaviors.same
        case x =>
          context.log.warn(s"Invalid command $x"); Behaviors.same
      }
    }
  }

  private def ccaThousand(): Long = Random.nextInt(500)+800 // a simulation of lines being read
}
