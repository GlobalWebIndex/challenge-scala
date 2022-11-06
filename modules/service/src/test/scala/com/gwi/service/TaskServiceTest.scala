package com.gwi.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink}
import akka.testkit.TestKit
import com.google.inject.{AbstractModule, Guice}
import com.gwi.database.model.memory.TaskState
import com.gwi.database.model.memory.dao.TaskRepository
import com.gwi.database.model.persistent.dao.JsonLineRepository
import com.gwi.service.client.HttpClient
import com.gwi.service.dto.{TaskCanceledResult, TaskDto}
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Succeeded}
import org.scalatest.wordspec.AsyncWordSpecLike

import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext}

class TaskServiceTest
    extends TestKit(ActorSystem("TaskServiceTestSpec"))
    with AsyncWordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  override def beforeEach(): Unit = {
    val jsonLineRepository = injector.getInstance(classOf[JsonLineRepository])
    import jsonLineRepository.session.profile.api._
    jsonLineRepository.session.db.run(jsonLineRepository.jsonLine.delete.transactionally)
    val taskRepository = injector.getInstance(classOf[TaskRepository])
    taskRepository.deleteAll()
  }

  override def afterEach(): Unit = {
    val jsonLineRepository = injector.getInstance(classOf[JsonLineRepository])
    import jsonLineRepository.session.profile.api._
    jsonLineRepository.session.db.run(jsonLineRepository.jsonLine.delete.transactionally)
    val taskRepository = injector.getInstance(classOf[TaskRepository])
    taskRepository.deleteAll()
  }

  private val injector = Guice.createInjector(new AbstractModule() {
    override def configure(): Unit = {
      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.getCsvFile(anyString())).thenReturn({
        val resourceUrl = getClass.getResource("/Lottery_Powerball_Winning_Numbers__Beginning_2010.csv")
        val sourceFilePath = Paths.get(resourceUrl.getPath)
        FileIO
          .fromPath(sourceFilePath)
      })

      bind(classOf[HttpClient]).toInstance(mockHttpClient)
      bind(classOf[ActorSystem]).toInstance(system)
      bind(classOf[Materializer]).toInstance(Materializer(system))
      bind(classOf[ExecutionContext]).toInstance(system.dispatcher)
    }
  })

  "TaskService" should {
    "correctly handle a create task request" in {
      val taskService = injector.getInstance(classOf[TaskService])
      val initialTasksF = taskService.getAllTasks().runWith(Sink.collection[TaskDto, List[TaskDto]])
      initialTasksF.map(initialTasks => assert(initialTasks.isEmpty))
      val taskId = taskService.createTask("randomUrl")
      val taskResult = taskService.getTaskInfo(taskId)
      assert(taskResult.isLeft)
      val taskF = taskResult.left.getOrElse(throw new RuntimeException).runWith(Sink.head)
      taskF.map(task => {
        assert(task.map(_.state).contains(TaskState.SCHEDULED.toString))
      })
      val nextTasksF = taskService.getAllTasks().runWith(Sink.collection[TaskDto, List[TaskDto]])
      nextTasksF.map(nextTasks => {
        assert(nextTasks.size == 1)
        assert(nextTasks.headOption.map(_.id).contains(taskId))
      })

      //cleanup
      taskService
        .getAllTasks()
        .runWith(Sink.collection[TaskDto, List[TaskDto]])
        .foreach(taskOpt => taskOpt.foreach(task => taskService.cancelTask(task.id)))
      Succeeded
    }

    "cancel a scheduled task" in {
      val taskService = injector.getInstance(classOf[TaskService])
      val initialTasksF = taskService.getAllTasks().runWith(Sink.collection[TaskDto, List[TaskDto]])
      initialTasksF.map(initialTasks => assert(initialTasks.isEmpty))
      val taskId = taskService.createTask("randomUrl")
      assert(taskService.cancelTask(taskId) == TaskCanceledResult.SUCCESS)
      val taskResult = taskService.getTaskInfo(taskId)
      assert(taskResult.isLeft)
      val canceledTask = taskResult.left.getOrElse(throw new RuntimeException).runWith(Sink.head)
      canceledTask.map(task => {
        assert(task.map(_.id).contains(taskId))
        assert(task.map(_.state).contains(TaskState.CANCELED.toString))
      })
      //cleanup
      taskService
        .getAllTasks()
        .runWith(Sink.collection[TaskDto, List[TaskDto]])
        .foreach(taskOpt => taskOpt.foreach(task => taskService.cancelTask(task.id)))
      Succeeded
    }

    "cancel a running task" in {
      val taskService = injector.getInstance(classOf[TaskService])
      val initialTasksF = taskService.getAllTasks().runWith(Sink.collection[TaskDto, List[TaskDto]])
      initialTasksF.map(initialTasks => assert(initialTasks.isEmpty))
      val taskId = taskService.createTask("randomUrl")
      val taskResult = taskService.getTaskInfo(taskId)
      assert(taskResult.isLeft)
      val runningTask = taskResult.left
        .getOrElse(throw new RuntimeException)
        .takeWhile(task => !task.map(_.state).contains(TaskState.RUNNING.toString), inclusive = true)
        .runWith(Sink.last)
      taskService.cancelTask(taskId)
      runningTask.map(task => {
        assert(task.map(_.id).contains(taskId))
        assert(task.map(_.state).contains(TaskState.RUNNING.toString))
      })

      val newTaskResult = taskService.getTaskInfo(taskId)
      assert(newTaskResult.isLeft)
      val canceledTask = taskResult.left
        .getOrElse(throw new RuntimeException)
        .takeWhile(task => !task.map(_.state).contains(TaskState.CANCELED.toString), inclusive = true)
        .runWith(Sink.last)
      canceledTask.map(task => {
        assert(task.map(_.id).contains(taskId))
        assert(task.map(_.state).contains(TaskState.CANCELED.toString))
      })
      //cleanup
      taskService
        .getAllTasks()
        .runWith(Sink.collection[TaskDto, List[TaskDto]])
        .foreach(taskOpt => taskOpt.foreach(task => taskService.cancelTask(task.id)))
      Succeeded
    }

    "respect the concurrency factor" in {
      val taskService = injector.getInstance(classOf[TaskService])
      val initialTasksF = taskService.getAllTasks().runWith(Sink.collection[TaskDto, List[TaskDto]])
      initialTasksF.map(initialTasks => assert(initialTasks.isEmpty))
      val taskIdList = for (_ <- 1 to 100) yield taskService.createTask("randomUrl")

      // Give some time for tasks to start
      Thread.sleep(1000)
      taskService
        .getAllTasks()
        .runWith(Sink.collection[TaskDto, List[TaskDto]])
        .map(taskList => {
          assert(taskList.size == taskList.size)
          taskIdList.foreach(id => assert(taskList.map(_.id).contains(id)))
          // concurrency factor in test conf is 3
          assert(taskList.count(_.state == TaskState.RUNNING.toString) == 3)
        })
      //cleanup
      taskService
        .getAllTasks()
        .runWith(Sink.collection[TaskDto, List[TaskDto]])
        .foreach(taskOpt => taskOpt.foreach(task => taskService.cancelTask(task.id)))
      Succeeded
    }

    "get json file from a done task" in {
      val taskService = injector.getInstance(classOf[TaskService])
      val initialTasksF = taskService.getAllTasks().runWith(Sink.collection[TaskDto, List[TaskDto]])
      initialTasksF.map(initialTasks => assert(initialTasks.isEmpty))
      val taskId = taskService.createTask("randomUrl")

      val taskResult = taskService.getTaskInfo(taskId)
      assert(taskResult.isLeft)
      val doneTaskF = taskResult.left
        .getOrElse(throw new RuntimeException)
        .takeWhile(
          task => {
            !task.map(_.state).contains(TaskState.DONE.toString)
          },
          inclusive = true
        )
        .runWith(Sink.last)
      val task = Await.result(doneTaskF, FiniteDuration(1, TimeUnit.MINUTES))
      assert(task.map(_.state).contains(TaskState.DONE.toString))
      assert(task.flatMap(_.result).nonEmpty)

      val jsonLinesResult = taskService.getJsonLines(taskId)
      assert(jsonLinesResult.isLeft)
      val jsonLinesF =
        jsonLinesResult.left.getOrElse(throw new RuntimeException).runWith(Sink.collection[String, List[String]])
      val jsonLines = Await.result(jsonLinesF, FiniteDuration(1, TimeUnit.MINUTES))
      assert(jsonLines.nonEmpty)

      //cleanup
      taskService
        .getAllTasks()
        .runWith(Sink.collection[TaskDto, List[TaskDto]])
        .foreach(taskOpt => taskOpt.foreach(task => taskService.cancelTask(task.id)))

      Succeeded
    }
  }

}
