package gwi

import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.Done
import scala.concurrent.Future

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
