package pool

import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import pool.akka.AkkaWorkerFactory
import pool.akka.AkkaWorkerPool
import pool.dependencies.Config
import pool.dependencies.Destination
import pool.dependencies.Namer
import pool.dependencies.Saver
import pool.interface.TaskCurrentState
import pool.interface.TaskFinishReason
import pool.interface.TaskShortInfo
import pool.interface.TaskState

import _root_.akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import _root_.akka.actor.typed.ActorRef
import _root_.akka.actor.typed.Behavior
import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.actor.typed.scaladsl.Behaviors
import _root_.akka.stream.scaladsl.Sink
import _root_.akka.stream.scaladsl.Source

import scala.concurrent.duration.Duration
import scala.util.Try

class WorkerPoolSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with ScalaFutures {
  def runWithContext(f: ActorContext[Unit] => Assertion): Assertion = {
    def extractor(replyTo: ActorRef[Try[Assertion]]): Behavior[Unit] =
      Behaviors.setup { ctx =>
        replyTo ! Try(f(ctx))
        Behaviors.stopped
      }
    val probe = testKit.createTestProbe[Try[Assertion]]()
    testKit.spawn(extractor(probe.ref))
    probe.receiveMessage(Duration(1, "m")).get
  }
  val config: Config = Config(concurrency = 2, timeout = Duration(3, "s"))
  val log: Logger = LoggerFactory.getLogger("WorkerPoolSpec")
  def poolMock(name: String, mockFactory: MockFactory = new MockFactory)(
      implicit ctx: ActorContext[_]
  ): WorkerPool[Long, Unit, Destination[Sink[Int, _]]] =
    AkkaWorkerPool(config, log, mockFactory, new MockSaver, new MockNamer, name)
  def poolReal(name: String, saver: MockSaver)(implicit
      ctx: ActorContext[_]
  ): WorkerPool[Long, Source[Int, _], Destination[Sink[Int, _]]] = {
    val factory = AkkaWorkerFactory[Int, Destination[Sink[Int, _]]]
    AkkaWorkerPool(config, log, factory, saver, new MockNamer, name)
  }

  "WorkerPool with fake factory" should {
    "create first task" in runWithContext { implicit ctx =>
      val pool = poolMock("createFirst")
      whenReady(pool.createTask(())) {
        _.map(taskInfo =>
          (taskInfo.taskId, taskInfo.linesProcessed, taskInfo.state)
        ) shouldBe Some(
          (1, 0, TaskCurrentState.Running())
        )
      }
    }
    "create second task" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val pool = poolMock("createSecond")
      val creatingFuture = for {
        _ <- pool.createTask(())
        last <- pool.createTask(())
      } yield last
      whenReady(creatingFuture) {
        _.map(taskInfo => (taskInfo.taskId, taskInfo.state)) shouldBe Some(
          (2, TaskCurrentState.Running())
        )
      }
    }
    "create third task in suspended state" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val pool = poolMock("createThird")
      val creatingFuture = for {
        _ <- pool.createTask(())
        _ <- pool.createTask(())
        last <- pool.createTask(())
      } yield last
      whenReady(creatingFuture) {
        _.map(taskInfo => (taskInfo.taskId, taskInfo.state)) shouldBe Some(
          (3, TaskCurrentState.Scheduled())
        )
      }
    }
    "run third task when first is finished" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val factory = new MockFactory
      val pool = poolMock("createThirdRunning", factory)
      val creatingFuture = for {
        firstTask <- pool.createTask(())
        _ <- pool.createTask(())
        _ = factory.workers(0).finish()
        thirdTask <- pool.createTask(())
      } yield thirdTask
      whenReady(creatingFuture) {
        _.map(taskInfo => (taskInfo.taskId, taskInfo.state)) shouldBe Some(
          (3, TaskCurrentState.Running())
        )
      }
    }
    "list tasks correctly" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val factory = new MockFactory
      val pool = poolMock("listTasks", factory)
      val creatingFuture = for {
        firstTask <- pool.createTask(())
        secondTask <- pool.createTask(())
        _ <- pool.createTask(())
        _ = factory.workers(0).finish()
        _ = factory.workers(1).fail()
        list <- pool.listTasks
      } yield list
      whenReady(creatingFuture) { list =>
        list should contain theSameElementsAs List(
          TaskShortInfo(1, TaskState.DONE),
          TaskShortInfo(2, TaskState.FAILED),
          TaskShortInfo(3, TaskState.RUNNING)
        )
      }
    }
    "get task details" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val factory = new MockFactory
      val pool = poolMock("taskDetails", factory)
      val creatingFuture = for {
        task <- pool.createTask(())
        _ = factory.workers(0).processed = 1000
        details <- pool.getTask(task.get.taskId)
      } yield details
      whenReady(creatingFuture) { details =>
        details.map(d => (d.taskId, d.linesProcessed, d.state)) shouldBe
          Some((1, 1000, TaskCurrentState.Running()))
      }
    }
    "get finished task details" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val factory = new MockFactory
      val pool = poolMock("finishedDetails", factory)
      val creatingFuture = for {
        task <- pool.createTask(())
        _ = factory.workers(0).finish()
        details <- pool.getTask(task.get.taskId)
      } yield details
      whenReady(creatingFuture) { details =>
        (for {
          d <- details
          finished <- d.state match {
            case TaskCurrentState.Finished(_, _, reason) => Some(reason)
            case _                                       => None
          }
        } yield (d.taskId, d.linesProcessed, finished)) shouldBe
          Some((1, 0, TaskFinishReason.Done))
      }
    }
    "get failed task details" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val factory = new MockFactory
      val pool = poolMock("failedDetails", factory)
      val creatingFuture = for {
        task <- pool.createTask(())
        _ = factory.workers(0).fail()
        details <- pool.getTask(task.get.taskId)
      } yield details
      whenReady(creatingFuture) { details =>
        (for {
          d <- details
          finished <- d.state match {
            case TaskCurrentState.Finished(_, _, reason) => Some(reason)
            case _                                       => None
          }
        } yield (d.taskId, d.linesProcessed, finished)) shouldBe
          Some((1, 0, TaskFinishReason.Failed))
      }
    }
    "cancel running task" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val pool = poolMock("cancelTask")
      val creatingFuture = for {
        task <- pool.createTask(())
        cancelled <- pool.cancelTask(task.get.taskId)
        details <- pool.getTask(task.get.taskId)
      } yield (cancelled, details)
      whenReady(creatingFuture) { case (cancelled, details) =>
        cancelled shouldBe defined
        (for {
          d <- details
          finished <- d.state match {
            case TaskCurrentState.Finished(_, _, reason) => Some(reason)
            case _                                       => None
          }
        } yield (d.taskId, d.linesProcessed, finished)) shouldBe
          Some((1, 0, TaskFinishReason.Cancelled))
      }
    }
    "pick up the next task when one finishes" in
      runWithContext { implicit ctx =>
        implicit val ec = ctx.executionContext
        val factory = new MockFactory
        val pool = poolMock("pickUpNext", factory)
        val creatingFuture = for {
          firstTask <- pool.createTask(())
          _ <- pool.createTask(())
          thirdTask <- pool.createTask(())
          _ = factory.workers(0).finish()
          details <- pool.getTask(thirdTask.get.taskId)
        } yield details
        whenReady(creatingFuture) { details =>
          details.map(_.state) shouldBe Some(TaskCurrentState.Running())
        }
      }
    "cancel all tasks" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val pool = poolMock("cancelAll")
      val creatingFuture = for {
        _ <- pool.createTask(())
        _ <- pool.createTask(())
        _ <- pool.createTask(())
        _ <- pool.cancelAll()
        list <- pool.listTasks
      } yield list
      whenReady(creatingFuture) { list =>
        list.map(_.state) shouldBe List.fill(3)(TaskState.CANCELLED)
      }
    }
    "stop before running anything" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val pool = poolMock("cancelAll")
      val creatingFuture = for {
        _ <- pool.cancelAll()
        list <- pool.listTasks
      } yield list
      whenReady(creatingFuture) { list =>
        list.map(_.state) shouldBe List.empty
      }
    }
  }
  "WorkerPool with real factory" should {
    "process the elements correctly" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val saver = new MockSaver
      val pool = poolReal("processElements", saver)
      val creatingFuture = for {
        task <- pool.createTask(Source(List(1, 2, 3)))
        _ = Thread.sleep(100)
        details <- pool.getTask(task.get.taskId)
      } yield details
      whenReady(creatingFuture) { details =>
        (for {
          d <- details
          finished <- d.state match {
            case TaskCurrentState.Finished(_, _, reason) =>
              Some(d.taskId, reason)
            case _ => None
          }
        } yield finished) shouldBe Some(1, TaskFinishReason.Done)
        saver.unmade shouldBe List((1, TaskFinishReason.Done))
        saver.saved should contain theSameElementsAs Map(1 -> List(1, 2, 3))
      }
    }
    "handle failure gracefully" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val saver = new MockSaver
      val failingSource =
        Source(List(1, 2)) ++ Source.lazySource(() =>
          Source.failed(new Exception("Failure"))
        )
      val pool = poolReal("processElements", saver)
      val creatingFuture = for {
        task <- pool.createTask(failingSource)
        _ = Thread.sleep(100)
        details <- pool.getTask(task.get.taskId)
      } yield details
      whenReady(creatingFuture) { details =>
        (for {
          d <- details
          finished <- d.state match {
            case TaskCurrentState.Finished(_, _, reason) =>
              Some(d.taskId, reason)
            case _ => None
          }
        } yield finished) shouldBe Some(1, TaskFinishReason.Failed)
        saver.unmade shouldBe List((1, TaskFinishReason.Failed))
        saver.saved shouldBe Map(1 -> List(1, 2))
      }
    }
    "cancel the task when necessary" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val saver = new MockSaver
      val pool = poolReal("processElements", saver)
      val creatingFuture = for {
        task <- pool.createTask(Source(List(1, 2)) ++ Source.never)
        _ = Thread.sleep(100)
        _ <- pool.cancelTask(task.get.taskId)
        details <- pool.getTask(task.get.taskId)
      } yield details
      whenReady(creatingFuture) { details =>
        (for {
          d <- details
          finished <- d.state match {
            case TaskCurrentState.Finished(_, _, reason) =>
              Some(d.taskId, reason)
            case _ => None
          }
        } yield finished) shouldBe Some(1, TaskFinishReason.Cancelled)
        saver.unmade shouldBe List((1, TaskFinishReason.Cancelled))
        saver.saved shouldBe Map(1 -> List(1, 2))
      }
    }
  }

  class MockSaver extends Saver[Long, Destination[Sink[Int, _]]] {
    var unmade: Vector[(Long, TaskFinishReason)] = Vector.empty
    var saved: Map[Long, Vector[Int]] = Map.empty
    def make(taskId: Long): Destination[Sink[Int, _]] =
      new Destination[Sink[Int, _]] {
        override def sink(): Sink[Int, _] = Sink.foreach { n =>
          val existing = saved.getOrElse(taskId, Vector.empty)
          saved = saved + (taskId -> (existing :+ n))
        }
        override def finalize(reason: TaskFinishReason): Unit =
          unmade = unmade :+ (taskId -> reason)
      }
  }
  class MockNamer extends Namer[Long] {
    var nextId: Long = 0
    def makeTaskId(): Long = {
      nextId = nextId + 1
      nextId
    }
  }
  class MockWorker(onDone: Long => Unit, onFailure: Long => Unit)
      extends Worker {
    var processed: Long = 0
    def finish(): Unit = onDone(processed)
    def fail(): Unit = onFailure(processed)
    def cancel(onCancel: Long => Unit): Unit = onCancel(processed)
    def currentCount(onCount: Long => Unit): Unit = onCount(processed)
  }
  class MockFactory extends WorkerFactory[Unit, Destination[Sink[Int, _]]] {
    var workers: Vector[MockWorker] = Vector.empty
    override def createWorker(
        source: Unit,
        destination: Destination[Sink[Int, _]],
        onDone: Long => Unit,
        onFailure: Long => Unit
    ): Worker = {
      val worker = new MockWorker(onDone, onFailure)
      workers = workers :+ worker
      worker
    }
  }
}
