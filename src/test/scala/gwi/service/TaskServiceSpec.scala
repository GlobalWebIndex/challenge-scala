package gwi.service

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.gwi.api.TaskState
import com.gwi.execution.TaskExecutor
import com.gwi.repository.TaskRepository
import com.gwi.service.TaskServiceImpl
import com.gwi.storage.TaskStorage
import gwi.SampleData
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}

class TaskServiceSpec
    extends TestKit(ActorSystem("TaskServiceSpec"))
    with AsyncFlatSpecLike
    with should.Matchers
    with AsyncMockFactory
    with SampleData {

  implicit val ec: ExecutionContextExecutor = system.dispatcher
  private val taskId = UUID.fromString("0b406535-21c8-4bcf-b79b-41ab7a01c264")

  "TaskService.cancelTask" should "cancel scheduled task" in {
    val repo = mock[TaskRepository]
    val storage = mock[TaskStorage]
    val executor = mock[TaskExecutor]

    val service = new TaskServiceImpl(repo, storage, executor)
    val runningTask = sampleTask.copy(state = TaskState.Running)

    (repo.getTask _).expects(taskId).returns(Future.successful(Some(runningTask)))
    (executor.cancelTaskExecution _).expects(taskId).returns(true)
    (repo.updateTask _).expects(*).returns(Future.successful(runningTask.id))

    service.cancelTask(runningTask.id) map (_ shouldEqual Right(runningTask.id))
  }

  it should "fail to cancel Done task" in {
    val repo = mock[TaskRepository]
    val storage = mock[TaskStorage]
    val executor = mock[TaskExecutor]

    val service = new TaskServiceImpl(repo, storage, executor)
    val doneTask = sampleTask.copy(state = TaskState.Done)

    (repo.getTask _).expects(taskId).returns(Future.successful(Some(doneTask)))
    (executor.cancelTaskExecution _).expects(*).never()
    (repo.updateTask _).expects(*).never()

    service.cancelTask(doneTask.id) map (_ shouldEqual Left(
      s"Task state is [${doneTask.state}], only Scheduled or Running tasks can be cancelled"
    ))
  }
}
