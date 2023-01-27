package pool

import pool.interface.TaskInfo
import pool.interface.TaskShortInfo

import scala.concurrent.Future

/** Schedules the tasks.
  *
  * Each task copies a stream of items from a given source to a given
  * destination
  * @tparam ID
  *   Unique task identifiers
  * @tparam IN
  *   Source address, such as URL
  * @tparam OUT
  *   Destination address, such as filename
  */
trait WorkerPool[ID, IN, OUT] {

  /** Adds a task to the pool
    * @param source
    *   Lazyly created source
    * @return
    *   Information about the added task, including it's identifier
    */
  def createTask(source: => IN): Future[Option[TaskInfo[ID, OUT]]]

  /** Lists all added tasks
    *
    * @return
    *   Abbreviated information about all the tasks
    */
  def listTasks: Future[Seq[TaskShortInfo[ID]]]

  /** Provides streaming information about the specified task
    *
    * @param taskId
    *   Task identifier
    * @return
    *   Information about that task, if it exists
    */
  def getTask(taskId: ID): Future[Option[TaskInfo[ID, OUT]]]

  /** Cancels, but not removes, the task from the pool
    *
    * @param taskId
    *   Task identifier
    * @return
    *   The number of processed items, if the task exists
    */
  def cancelTask(taskId: ID): Future[Option[Long]]

  /** Cancels all tasks and prevents new ones from being created */
  def cancelAll(): Future[Unit]
}
