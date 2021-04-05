package gwi

import akka.Done
import akka.actor.testkit.typed.scaladsl._
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.Uri
import akka.pattern.StatusReply
import akka.pattern.StatusReply.Success
import gwi.Manager._
import gwi.ManagerSpec._
import org.scalatest._
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import scala.concurrent.Future

object ManagerSpec {
  final case class WorkerStarted(
      task: Task,
      respondTo: ActorRef[Worker.Response],
      workerProbe: TestProbe[Worker.Command],
      workerRef: ActorRef[Worker.Command]
  )

  val source1: Uri = Uri("/source1.csv")
  val source2: Uri = Uri("/source2.csv")
  val source3: Uri = Uri("/source3.csv")
}

class ManagerSpec extends ActorTestKitBase(ActorTestKit()) with AsyncFlatSpecLike with Matchers with BeforeAndAfterAll {
  override def afterAll(): Unit = testKit.shutdownTestKit()

  "Manager" should "handle a create task request" in withManager { (managerRef, workersProbe) =>
    val probe = testKit.createTestProbe[StatusReply[TaskId]]()
    LoggingTestKit.debug(messageIncludes = "Creating and running a new task processing /source1.csv").expect {
      managerRef ! Create(source1, probe.ref)
    }

    val id = probe.receiveMessage()
    id shouldBe Success(TaskId(1))

    val WorkerStarted(task, respondTo, _, workerRef) = workersProbe.receiveMessage()
    task.id shouldBe TaskId(1)

    managerRef
      .askWithStatus(Get(id.getValue, _))
      .flatMap { t =>
        t shouldBe a[RunningTask]
        LoggingTestKit.debug(messageIncludes = "Worker for task 1 finished").expect {
          LoggingTestKit.debug(messageIncludes = "Worker for task 1 terminated").expect {
            respondTo ! Worker.WorkerFinished(id.getValue, TaskStats(1, 1))
            workerRef ! Worker.Stop
          }
        }

        managerRef.askWithStatus(Get(id.getValue, _))
      }
      .flatMap { t =>
        t shouldBe a[DoneTask]
      }
  }

  it should "enqueue the third task" in withManager { (managerRef, _) =>
    val probe = testKit.createTestProbe[StatusReply[TaskId]]()
    managerRef ! Create(source1, probe.ref)
    managerRef ! Create(source2, probe.ref)
    LoggingTestKit.debug(messageIncludes = "Creating and enqueueing a new task processing /source3.csv").expect {
      managerRef ! Create(source3, probe.ref)
    }

    val id = probe.receiveMessages(3).last
    id shouldBe Success(TaskId(3))

    managerRef
      .askWithStatus(Get(id.getValue, _))
      .flatMap { t =>
        t shouldBe a[ScheduledTask]
        LoggingTestKit.debug(messageIncludes = "Cancelling task 3").expect {
          val canceledProbe = testKit.createTestProbe[StatusReply[Done]]()
          managerRef ! Cancel(id.getValue, canceledProbe.ref)
          canceledProbe.receiveMessage()
        }

        managerRef.askWithStatus(Get(id.getValue, _))
      }
      .flatMap { t =>
        t shouldBe a[CanceledTask]
      }
  }

  it should "run enqueued task when a slot becomes available" in withManager { (managerRef, workersProbe) =>
    val probe = testKit.createTestProbe[StatusReply[TaskId]]()
    managerRef ! Create(source1, probe.ref)
    managerRef ! Create(source2, probe.ref)
    managerRef ! Create(source3, probe.ref)

    probe.receiveMessages(3)

    val WorkerStarted(task2, _, worker2Probe, worker2Ref) = workersProbe.receiveMessages(2).last
    task2.id shouldBe TaskId(2)

    LoggingTestKit.debug(messageIncludes = "Worker for task 2 terminated").expect {
      LoggingTestKit.debug(messageIncludes = "Scheduling task 3").expect {
        val canceledProbe = testKit.createTestProbe[StatusReply[Done]]()
        managerRef ! Cancel(task2.id, canceledProbe.ref)
        canceledProbe.receiveMessage()

        worker2Probe.expectMessageType[Worker.Cancel.type]
        worker2Ref ! Worker.Stop

        val WorkerStarted(task3, _, _, _) = workersProbe.receiveMessage()
        task3.id shouldBe TaskId(3)
      }
    }

    managerRef.askWithStatus(Get(TaskId(3), _)).flatMap { t =>
      t shouldBe a[RunningTask]
    }
  }

  // TODO: more Manager tests

  def withManager(f: (ActorRef[Command], TestProbe[WorkerStarted]) => Future[Assertion]): Future[Assertion] = {
    val workersProbe = testKit.createTestProbe[WorkerStarted]()
    val factory: Worker.Factory = (task, respondTo) =>
      Behaviors.setup[Worker.Command] { context =>
        val workerProbe = testKit.createTestProbe[Worker.Command]()
        workersProbe.ref ! WorkerStarted(task, respondTo, workerProbe, context.self)
        val behavior = Behaviors.receivePartial[Worker.Command] {
          case (_, Worker.Stop) => Behaviors.stopped
          case (_, _)           => Behaviors.same
        }
        Behaviors.monitor(workerProbe.ref, behavior)
      }

    val manager = testKit.spawn(Manager(factory))
    f(manager, workersProbe).map { assertion =>
      testKit.stop(manager)
      assertion
    }
  }
}
