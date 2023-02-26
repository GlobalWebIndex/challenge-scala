package cz.vlasec.gwi.csvimport

package object service {
  import akka.actor.typed.ActorRef
  import cz.vlasec.gwi.csvimport.service.CsvService.ServiceCommand
  import cz.vlasec.gwi.csvimport.service.CsvTask.TaskCommand
  import cz.vlasec.gwi.csvimport.service.CsvWorker.WorkerCommand
  import cz.vlasec.gwi.csvimport.service.Overseer.OverseerCommand

  final case class EnqueueTaskResponse(taskId: TaskId)

  case class StatusFailure private[service](reason: String)

  case class TaskStatusReport private[service](
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
  private[service] type TaskRef = ActorRef[TaskCommand]
  private[service] type WorkerRef = ActorRef[WorkerCommand]
  private[service] type ServiceRef = ActorRef[ServiceCommand]
  private[service] type OverseerRef = ActorRef[OverseerCommand]
  type CsvStatusResponse = Either[StatusFailure, TaskStatusReport]
}
