package conversion

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import models.ConversionMessage
import models.TaskCurrentState
import models.TaskInfo

import java.io.File

object ConversionActor {
  def create(concurrency: Int): ActorRef[ConversionMessage] =
    ActorSystem(
      Behaviors.receiveMessage[ConversionMessage](_ match {
        case ConversionMessage.CreateTask(url, replyTo) =>
          replyTo ! TempData.newTaskInfo
          Behaviors.same
        case ConversionMessage.ListTasks(replyTo) =>
          replyTo ! TempData.allTaskInfos
          Behaviors.same
        case ConversionMessage.GetTask(taskId, replyTo) =>
          replyTo ! TempData.allTaskInfos.find(_.taskId == taskId)
          Behaviors.same
        case ConversionMessage.CancelTask(taskId, replyTo) =>
          replyTo ! TempData.allTaskInfos.exists(_.taskId == taskId)
          Behaviors.same
      }),
      "ConversionActor"
    )
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
