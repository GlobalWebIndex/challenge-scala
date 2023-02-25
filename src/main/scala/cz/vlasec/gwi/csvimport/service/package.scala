package cz.vlasec.gwi.csvimport

package object service {

  import akka.actor.typed.ActorRef
  import cz.vlasec.gwi.csvimport.service.CsvService.CsvServiceCommand
  import cz.vlasec.gwi.csvimport.service.CsvTask.CsvTaskCommand
  import cz.vlasec.gwi.csvimport.service.CsvWorker.CsvWorkerCommand

  final case class EnqueueCsvTaskResponse(taskId: TaskId)

  case class StatusFailure private[service](reason: String)

  case class CsvTaskStatusReport private[service](
                                                   taskId: TaskId,
                                                   state: String,
                                                   linesProcessed: Long,
                                                   averageLinesProcessed: Float,
                                                   result: Option[ResultPath] = None
                                                 )

  private[service] case class CsvDetail(url: CsvUrl)

  type ResultPath = String
  type CsvUrl = String
  type TaskId = Long
  private[service] type TaskRef = ActorRef[CsvTaskCommand]
  private[service] type WorkerRef = ActorRef[CsvWorkerCommand]
  private[service] type ServiceRef = ActorRef[CsvServiceCommand]
  type CsvStatusResponse = Either[StatusFailure, CsvTaskStatusReport]
}
