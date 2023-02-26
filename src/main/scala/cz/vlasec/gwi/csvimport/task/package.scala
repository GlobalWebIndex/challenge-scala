package cz.vlasec.gwi.csvimport

package object task {
  import akka.actor.typed.ActorRef
  import cz.vlasec.gwi.csvimport.task.Service.ServiceCommand
  import cz.vlasec.gwi.csvimport.task.Task.TaskCommand
  import cz.vlasec.gwi.csvimport.task.Worker.WorkerCommand
  import cz.vlasec.gwi.csvimport.task.Overseer.OverseerCommand

  final case class EnqueueTaskResponse(taskId: TaskId)

  case class StatusFailure private[task](reason: String)

  case class TaskStatusReport private[task](
                                                   taskId: TaskId,
                                                   state: String,
                                                   linesProcessed: Long,
                                                   averageLinesProcessed: Float,
                                                   result: Option[ResultPath] = None
                                                 )

  private[task] case class CsvDetail(url: CsvUrl)

  type ResultPath = String
  type CsvUrl = String
  type TaskId = Long
  private[task] type TaskRef = ActorRef[TaskCommand]
  private[task] type WorkerRef = ActorRef[WorkerCommand]
  private[task] type ServiceRef = ActorRef[ServiceCommand]
  private[task] type OverseerRef = ActorRef[OverseerCommand]
  type CsvStatusResponse = Either[StatusFailure, TaskStatusReport]
}
