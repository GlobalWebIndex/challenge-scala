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

import scala.concurrent.{ExecutionContextExecutor, Future}

class TaskServiceSpec
    extends TestKit(ActorSystem("TaskServiceSpec"))
    with AsyncFlatSpecLike
    with should.Matchers
    with AsyncMockFactory
    with SampleData {

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  "TaskService.cancelTask" should "cancel scheduled task" in {
    val repo = mock[TaskRepository]
    val storage = mock[TaskStorage]
    val executor = mock[TaskExecutor]

    val service = new TaskServiceImpl(repo, storage, executor)
    val runningTask = sampleTask.copy(state = TaskState.Running)

    (repo.getTask _).expects(sampleTask.id).returns(Future.successful(Some(runningTask)))
    (executor.cancelTaskExecution _).expects(sampleTask.id).returns(true)
    (repo.upsertTask _).expects(*).returns(Future.successful(runningTask.id))
    (repo.getTask _).expects(sampleTask.id).returns(Future.successful(Some(runningTask.copy(state = TaskState.Canceled))))

    service.cancelTask(runningTask.id) map (_ shouldEqual Right(runningTask.id))
  }

  it should "fail to cancel Done task" in {
    val repo = mock[TaskRepository]
    val storage = mock[TaskStorage]
    val executor = mock[TaskExecutor]

    val service = new TaskServiceImpl(repo, storage, executor)
    val doneTask = sampleTask.copy(state = TaskState.Done)

    (repo.getTask _).expects(sampleTask.id).returns(Future.successful(Some(doneTask)))
    (executor.cancelTaskExecution _).expects(*).never()
    (repo.upsertTask _).expects(*).never()

    service.cancelTask(doneTask.id) map (_ shouldEqual Left(
      s"Task state is [${doneTask.state}], only Scheduled or Running tasks can be cancelled"
    ))
  }
}
