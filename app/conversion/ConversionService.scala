package conversion

import models.TaskCurrentState
import models.TaskInfo

import java.io.File
import scala.concurrent.Future

class ConversionService(config: ConversionConfig) {
  def createTask(url: String): Future[TaskInfo] =
    Future.successful(TempData.newTaskInfo)
  def listTasks: Future[Seq[TaskInfo]] =
    Future.successful(TempData.allTaskInfos)
  def getTask(taskId: String): Future[Option[TaskInfo]] =
    Future.successful(TempData.allTaskInfos.find(_.taskId == taskId))
  def cancelTask(taskId: String): Future[Boolean] =
    Future.successful(TempData.allTaskInfos.exists(_.taskId == taskId))
}

object TempData {
  val allTaskInfos = List(
    TaskInfo(
      "foo",
      0,
      System.currentTimeMillis,
      TaskCurrentState.Running
    ),
    TaskInfo(
      "bar",
      100,
      System.currentTimeMillis,
      TaskCurrentState.Done(
        System.currentTimeMillis + 1000,
        new File("build.sbt")
      )
    )
  )
  val newTaskInfo =
    TaskInfo(
      "foo",
      0,
      System.currentTimeMillis,
      TaskCurrentState.Running
    )
}
