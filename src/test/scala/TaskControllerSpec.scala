package com.gwi.karelsk

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Inside.inside
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues._
import org.scalatest.concurrent.Eventually.eventually

import java.net.URI
import java.nio.file.Path
import scala.annotation.unused

class TaskControllerSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers {
  import Task._
  import TaskController._
  import TaskWorker.ProgressReport

  val testKit = ActorTestKit()

  sealed trait WorkerCommand
  final case object WorkerStop extends WorkerCommand
  final case class SendProgress(p: Progress) extends WorkerCommand
  final case class WorkerSpawned(id: Id, ref: ActorRef[WorkerCommand])

  def workerMock(spawnListener: Option[ActorRef[WorkerSpawned]])
                (id: Task.Id, @unused from: URI, @unused result: Path, reportTo: ActorRef[ProgressReport]): Behavior[Nothing] =
    (Behaviors setup[WorkerCommand] { context =>
      spawnListener foreach (_ ! WorkerSpawned(id, context.self))
      Behaviors receiveMessage[WorkerCommand] {
        case WorkerStop => Behaviors.stopped
        case SendProgress(p) =>
          reportTo ! TaskWorker.ProgressReport(id, p)
          Behaviors.same
      }
    }).narrow

  implicit class ControllerOps(val controller: ActorRef[Command])
                              (implicit createTaskProbe: TestProbe[Id],
                                        listTasksProbe: TestProbe[Set[Id]],
                                        taskDetailProbe: TestProbe[Option[Task]]) {

    def createTask(uri: String = ""): Id = {
      controller ! CreateTask(new URI(uri), createTaskProbe.ref)
      createTaskProbe.receiveMessage()
    }

    def listTasks: Set[Id] = {
      controller ! ListTasks(listTasksProbe.ref)
      listTasksProbe.receiveMessage()
    }

    def queryTask(id: Id): Option[Task] = {
      controller ! TaskDetail(id, taskDetailProbe.ref)
      taskDetailProbe.receiveMessage()
    }
  }

  def taskControllerBehavior(sl: Option[ActorRef[WorkerSpawned]] = None): Behavior[Command] =
    TaskController(workerMock(sl), Path.of("/tmp"))

  "TaskController" must {
    implicit val createTaskProbe: TestProbe[Id] = testKit.createTestProbe[Id]()
    implicit val listTasksProbe: TestProbe[Set[Id]] = testKit.createTestProbe[Set[Id]]()
    implicit val taskDetailProbe: TestProbe[Option[Task]] = testKit.createTestProbe[Option[Task]]()

    "control no tasks after creation" in {
      val controller = testKit.spawn(taskControllerBehavior())
      controller.listTasks should be (empty)
    }

    "run one created task until finished" in {
      val workersProbe = testKit.createTestProbe[WorkerSpawned]()
      val controller = testKit.spawn(taskControllerBehavior(Some(workersProbe.ref)))

      val id = controller.createTask()
      val worker = workersProbe.receiveMessage().ref
      controller.listTasks should contain only id

      val running = controller.queryTask(id)
      running.value.id should be (id)
      running.value shouldBe a [Running]

      worker ! WorkerStop
      workersProbe.expectTerminated(worker)
      eventually { controller.queryTask(id).value shouldBe a [Done] }
    }

    "not invent an nonexistent task" in {
      val controller = testKit.spawn(taskControllerBehavior())
      val id = controller.createTask("e")
      controller.queryTask(id.next) should be (empty)
    }

    "run only 2 tasks at a time" in {
      val workersProbe = testKit.createTestProbe[WorkerSpawned]()
      val controller = testKit.spawn(taskControllerBehavior(Some(workersProbe.ref)))

      val id1 = controller.createTask("a")
      val worker1 = workersProbe.receiveMessage().ref
      val id2 = controller.createTask("b")
      val id3 = controller.createTask("c")

      controller.listTasks should contain only (id1, id2, id3)

      controller.queryTask(id1).value shouldBe a [Running]
      controller.queryTask(id2).value shouldBe a [Running]
      controller.queryTask(id3).value shouldBe a [Scheduled]

      worker1 ! WorkerStop
      workersProbe.expectTerminated(worker1)
      eventually { controller.queryTask(id1).value shouldBe a [Done] }
      eventually { controller.queryTask(id3).value shouldBe a [Running] }
    }

    "update progress of a running task" in {
      val workersProbe = testKit.createTestProbe[WorkerSpawned]()
      val controller = testKit.spawn(taskControllerBehavior(Some(workersProbe.ref)))

      val id = controller.createTask("x")
      val worker = workersProbe.expectMessageType[WorkerSpawned].ref
      inside(controller.queryTask(id)) { case Some(running: Running) => running.progress should be ((0, 0)) }

      worker ! SendProgress(1, 10)
      worker ! SendProgress(2, 20)
      worker ! WorkerStop
      taskDetailProbe.expectTerminated(worker)
      eventually { controller.queryTask(id).value shouldBe a [Done] }
      inside(controller.queryTask(id)) { case Some(done: Done) => done.progress should be ((2, 20)) }
    }

    // TODO: test task cancelling of cancellable/terminal task
    // TODO: test task failure
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()
}
