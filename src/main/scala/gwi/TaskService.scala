package gwi

import akka._
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Source
import akka.util._
import gwi.Manager._
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object TaskService {
  def apply(ref: ActorRef[Command], storage: Storage)(implicit t: Timeout, system: ActorSystem[_]): TaskService =
    new TaskServiceImpl(ref, storage)
}

trait TaskService {

  /** Creates and enqueues a new Task with the specified source. */
  def create(source: Uri): Future[TaskId]

  /** Retrieves a list of all tasks seen by the manager. */
  def getAll: Future[List[Task]]

  /** Gets a stream of updates for the task with the specified id. Stream finishes
    * when the task reaches a terminal state (see Task.isTerminal).
    */
  def get(id: TaskId): Future[Source[Task, _]]

  /** Tries to cancel the task with the specified id. Fails if task cannot be canceled. */
  def cancel(id: TaskId): Future[Done]

  /** Tries to retrieve JSON converted by the task with the specified id. Fails if task
    * is not in state Done.
    */
  def result(id: TaskId): Future[Source[ByteString, _]]
}

private class TaskServiceImpl(ref: ActorRef[Command], storage: Storage)(implicit t: Timeout, system: ActorSystem[_])
    extends TaskService {
  import system.executionContext

  override def create(source: Uri): Future[TaskId] =
    ref.askWithStatus(Create(source, _))

  override def getAll: Future[List[Task]] =
    ref.askWithStatus(GetAll)

  override def get(id: TaskId): Future[Source[Task, NotUsed]] = {
    def getTask: Future[Task] = ref.askWithStatus(Get(id, _))
    getTask.map { task =>
      Source
        .single(task)
        .concat(Source.tick(2.seconds, 2.seconds, NotUsed).mapAsync(parallelism = 1)(_ => getTask))
        .takeWhile(t => !Task.isTerminal(t), inclusive = true)
    }
  }

  override def cancel(id: TaskId): Future[Done] =
    ref.askWithStatus(Cancel(id, _))

  override def result(id: TaskId): Future[Source[ByteString, _]] =
    ref.askWithStatus(Get(id, _)).flatMap {
      case DoneTask(`id`, _, _, _) => Future.successful(storage.source(id))
      case _                       => Future.failed(BadRequestError("Task is not in state DONE"))
    }
}
