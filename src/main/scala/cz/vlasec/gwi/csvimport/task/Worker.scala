package cz.vlasec.gwi.csvimport.task

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{Behavior, Scheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl.{FileIO, Flow, Sink}
import akka.util.{ByteString, Timeout}

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

/**
 * The actor that represents the actual processing power allocated to convert CSV to JSON.
 * When idle, worker reports itself to its overseer and awaits a task to process.
 * When in busy state, worker monitors the stream that processes the data and takes metrics.
 */
private[task] object Worker {
  sealed trait WorkerCommand
  final case class ProcessTask(taskRef: TaskRef) extends WorkerCommand
  private case class StreamTask(executor: ExecutorService) extends WorkerCommand
  final case object CancelTask extends WorkerCommand
  private case object FailTask extends WorkerCommand
  // The future of these two is uncertain after the introduction of the actual stream.
  private case object LineProcessed extends WorkerCommand
  private case object FinishTask extends WorkerCommand


  def apply(overseerRef: OverseerRef): Behavior[WorkerCommand] = idle(overseerRef)

  private def idle(overseerRef: OverseerRef): Behavior[WorkerCommand] = Behaviors.setup { context =>
    overseerRef ! Overseer.IdleWorker(context.self)
    Behaviors.receiveMessage {
      case ProcessTask(taskRef) =>
        implicit val timeout: Timeout = 100.millis
        implicit val scheduler: Scheduler = context.system.scheduler
        val detail = Await.result(taskRef.ask(ref => Task.Run(context.self, ref)), timeout.duration)
        context.log.info(s"Processing CSV at ${detail.url}")

        val executor = Executors.newSingleThreadExecutor()
        implicit val classicSystem: ActorSystem = context.system.classicSystem
        implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutorService(executor)
        Http().singleRequest(Get(detail.url))
          .onComplete(httpOnComplete(context, executor, filename(taskRef)))

        initiating(taskRef, overseerRef)
      case x =>
        context.log.warn(s"Invalid command $x"); Behaviors.same
    }
  }

  private def httpOnComplete(context: ActorContext[WorkerCommand], executor: ExecutorService, filename: String)
  : Try[HttpResponse] => Unit = {
    case Success(response) =>
      if (response.status.isFailure()) {
        context.log.error(s"HTTP request failed with status ${response.status}.")
        context.self ! FailTask
      } else {
        implicit val mat: Materializer = Materializer(context)
        // TODO handle these bloody redirects
        val result = response.entity.dataBytes
          .via(flow(context.self))
          .runWith(FileIO.toPath(tempDirPath.resolve(filename)))
        context.self ! StreamTask(executor)
        implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutorService(executor)
        result.onComplete(_ => context.self ! FinishTask)
      }
    case Failure(exception) =>
      context.log.error(s"Failed to start downloading CSV, because of exception thrown:", exception)
      context.self ! FailTask
  }

  private def initiating(taskRef: TaskRef, overseerRef: OverseerRef): Behavior[WorkerCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case StreamTask(executionContext) =>
        processing(executionContext, taskRef, overseerRef)
      case FailTask =>
        taskRef ! Task.Fail
        idle(overseerRef)
      case CancelTask =>
        context.log.warn(s"Cancel during initiation not implemented yet")
        Behaviors.same
      case x =>
        context.log.warn(s"Invalid command $x"); Behaviors.same
    }
  }

  private def processing(executor: ExecutorService, taskRef: TaskRef, overseerRef: OverseerRef)
  : Behavior[WorkerCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case CancelTask =>
        executor.shutdownNow()
        idle(overseerRef)
      case LineProcessed =>
        taskRef ! Task.ProcessLines(1)
        Behaviors.same
      case FinishTask =>
        taskRef ! Task.Finish(filename(taskRef))
        idle(overseerRef)
      case x =>
        context.log.warn(s"Invalid command $x"); Behaviors.same
    }
  }

  private def flow(selfRef: WorkerRef): Flow[ByteString, ByteString, NotUsed] = {
    CsvParsing.lineScanner()
      .via(CsvToMap.toMapAsStrings())
      .alsoTo(Sink.foreach(_ => selfRef ! LineProcessed)) // should probably be optimized
      .via(Flow.fromFunction {
        import io.circe.syntax._
        map => ByteString(map.asJson.noSpaces + "\n")
      })
  }

  private def filename(taskRef: TaskRef) = s"${taskRef.path.name}.jsonl"
}
