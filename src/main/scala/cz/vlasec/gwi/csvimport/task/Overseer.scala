package cz.vlasec.gwi.csvimport.task

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

/**
 * Overseer (or supervisor) spawns workers and manages them.
 * All workers are sent to task service, the initial ones, returned ones and respawned ones.
 * The workers only know to return to their overseer, to assign them to service again is overseer's job.
 */
object Overseer {
  sealed trait OverseerCommand
  private[task] final case class IdleWorker(workerRef:WorkerRef) extends OverseerCommand
  private[Overseer] final case class WorkerStopped(workerId: Long) extends OverseerCommand

  def apply(workerCount: Int, serviceRef: ServiceRef)
  : Behavior[OverseerCommand] = Behaviors.setup { context =>
    (1 to workerCount).foreach(startWorker(context, _))
    context.log.info(s"Sending $workerCount idle workers to ${serviceRef.path.name}.")
    overseeing(serviceRef)
  }

  private def overseeing(serviceRef: ServiceRef)
  : Behavior[OverseerCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case IdleWorker(workerRef) =>
        serviceRef ! Service.IdleWorker(workerRef)
        Behaviors.same
      case WorkerStopped(workerId) =>
        context.log.warn(s"Restarting stopped worker-$workerId.")
        startWorker(context, workerId)
        Behaviors.same
      case x =>
        context.log.warn(s"Invalid command: $x")
        Behaviors.same
    }
  }

  private def startWorker(context: ActorContext[OverseerCommand], workerId: Long): Unit = {
    val workerRef = context.spawn(Worker(context.self), s"worker-$workerId")
    context.watchWith(workerRef, WorkerStopped(workerId))
  }
}
