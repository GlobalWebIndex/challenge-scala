package cz.vlasec.gwi.csvimport.task

import akka.NotUsed
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, Scheduler}
import akka.stream.{KillSwitches, Materializer, SharedKillSwitch}
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl.{FileIO, Flow, Sink}
import akka.util.{ByteString, Timeout}
import cz.vlasec.gwi.csvimport.Sourceror
import cz.vlasec.gwi.csvimport.Sourceror.SourceConsumerRef

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

/**
 * The actor that represents the actual processing power allocated to convert CSV to JSON.
 * When idle, worker reports itself to its overseer and awaits a task to process.
 * When in busy state, worker monitors the stream that processes the data and takes metrics.
 */
private[task] object Worker {
  sealed trait WorkerCommand
  final case class ProcessTask(taskRef: TaskRef) extends WorkerCommand
  final case object CancelTask extends WorkerCommand
  final case object StreamCanceled extends WorkerCommand
  private case class FailTask(exception: Throwable) extends WorkerCommand
  private case object LineProcessed extends WorkerCommand
  private case object FinishTask extends WorkerCommand

  def apply(overseerRef: OverseerRef): Behavior[WorkerCommand] = idle(overseerRef)

  private def idle(overseerRef: OverseerRef): Behavior[WorkerCommand] = Behaviors.setup { context =>
    val selfRef = context.self
    overseerRef ! Overseer.IdleWorker(selfRef)
    Behaviors.receiveMessage {
      case ProcessTask(taskRef) =>
        // Doing this entire process synchronously to avoid handling things like cancel coming during HTTP initiation.
        implicit val timeout: Timeout = 5.seconds
        implicit val scheduler: Scheduler = context.system.scheduler
        val detail = Await.result(taskRef.ask(ref => Task.Run(selfRef, ref)), timeout.duration)
        context.log.info(s"Processing CSV at ${detail.url}")
        Await.result(overseerRef.ask { ref: SourceConsumerRef =>
          Overseer.ContactSourceror(Sourceror.InitiateHttp(detail.url, ref))
        }, timeout.duration) match {
          case Some(source) =>
            implicit val mat: Materializer = Materializer(context)
            implicit val executionContext: ExecutionContext = context.system.executionContext
            val killSwitch = KillSwitches.shared(taskRef.path.toString)
            source
              .via(flow(killSwitch, selfRef))
              .runWith(FileIO.toPath(tempDirPath.resolve(filename(taskRef))))
              .onComplete {
                case Failure(exception) => exception.getCause match {
                  case CancelTaskException() =>
                    selfRef ! StreamCanceled
                  case _ =>
                    selfRef ! FailTask(exception)
                }
                case Success(_) =>
                  selfRef ! FinishTask
              }

            processing(killSwitch, taskRef, overseerRef)
          case None =>
            taskRef ! Task.Fail
            idle(overseerRef)
        }
      case x =>
        context.log.warn(s"Invalid command $x"); Behaviors.same
    }
  }

  private def processing(killSwitch: SharedKillSwitch, taskRef: TaskRef, overseerRef: OverseerRef)
  : Behavior[WorkerCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case LineProcessed =>
        taskRef ! Task.ProcessLines(1)
        Behaviors.same
      case CancelTask =>
        killSwitch.abort(new CancelTaskException)
        Behaviors.same
      case StreamCanceled =>
        taskRef ! Task.WorkerCanceled
        idle(overseerRef)
      case FinishTask =>
        taskRef ! Task.Finish(filename(taskRef))
        idle(overseerRef)
      case FailTask(exception) =>
        context.log.error("Exception during download of CSV", exception)
        taskRef ! Task.Fail
        idle(overseerRef)
      case x =>
        context.log.warn(s"Invalid command $x"); Behaviors.same
    }
  }

  // The actual CSV to JSON processing. It is funny how little code had to be written for that and how much for all else
  private def flow(killSwitch: SharedKillSwitch, selfRef: WorkerRef): Flow[ByteString, ByteString, NotUsed] = {
    CsvParsing.lineScanner()
      .via(killSwitch.flow)
      .via(CsvToMap.toMapAsStrings())
      .alsoTo(Sink.foreach(_ => selfRef ! LineProcessed)) // should probably be optimized, it's a lot of messages.
      .via(Flow.fromFunction {
        import io.circe.syntax._
        map => ByteString(map.asJson.noSpaces + "\n")
      })
  }

  private def filename(taskRef: TaskRef) = s"${taskRef.path.name}.jsonl"

  private case class CancelTaskException() extends RuntimeException
}
