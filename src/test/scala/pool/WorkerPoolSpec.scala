package pool

import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import pool.WorkerFactory
import pool.dependencies.Config
import pool.dependencies.Fetch
import pool.dependencies.Namer
import pool.dependencies.Saver
import pool.interface.TaskCurrentState
import pool.interface.TaskFinishReason
import pool.interface.TaskShortInfo
import pool.interface.TaskState

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

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
  ): WorkerPool[Long, Unit, String] =
    WorkerPool(config, log, mockFactory, new MockSaver, new MockNamer, name)
  def poolReal(
      name: String
  )(implicit ctx: ActorContext[_]): WorkerPool[Long, String, String] = {
    val saver = new MockSaver
    val factory = WorkerFactory(new MockFetch, saver)
    WorkerPool(config, log, factory, saver, new MockNamer, name)
  }

  "WorkerPool with fake factory" should {
    "create first task" in runWithContext { implicit ctx =>
      val pool = poolMock("createFirst")
      whenReady(pool.createTask(())) { taskInfo =>
        taskInfo.taskId shouldBe 1
        taskInfo.linesProcessed shouldBe 0
        taskInfo.state shouldBe TaskCurrentState.Running()
      }
    }
    "create second task" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val pool = poolMock("createSecond")
      val creatingFuture = for {
        _ <- pool.createTask(())
        last <- pool.createTask(())
      } yield last
      whenReady(creatingFuture) { taskInfo =>
        taskInfo.taskId shouldBe 2
        taskInfo.state shouldBe TaskCurrentState.Running()
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
      whenReady(creatingFuture) { taskInfo =>
        taskInfo.taskId shouldBe 3
        taskInfo.state shouldBe TaskCurrentState.Scheduled()
      }
    }
    "run third task when first is finished" in runWithContext { implicit ctx =>
      implicit val ec = ctx.executionContext
      val factory = new MockFactory
      val pool = poolMock("createThirdRunning", factory)
      val creatingFuture = for {
        firstTask <- pool.createTask(())
        _ <- pool.createTask(())
        _ = factory.workers.get(firstTask.taskId).get.finish()
        thirdTask <- pool.createTask(())
      } yield thirdTask
      whenReady(creatingFuture) { taskInfo =>
        taskInfo.taskId shouldBe 3
        taskInfo.state shouldBe TaskCurrentState.Running()
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
        _ = factory.workers.get(firstTask.taskId).get.finish()
        _ = factory.workers.get(secondTask.taskId).get.fail()
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
        _ = factory.workers.get(task.taskId).get.processed = 1000
        details <- pool.getTask(task.taskId)
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
        _ = factory.workers.get(task.taskId).get.finish()
        details <- pool.getTask(task.taskId)
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
        _ = factory.workers.get(task.taskId).get.fail()
        details <- pool.getTask(task.taskId)
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
        cancelled <- pool.cancelTask(task.taskId)
        details <- pool.getTask(task.taskId)
      } yield (cancelled, details)
      whenReady(creatingFuture) { case (cancelled, details) =>
        cancelled shouldBe true
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
          _ = factory.workers.get(firstTask.taskId).get.finish()
          details <- pool.getTask(thirdTask.taskId)
        } yield details
        whenReady(creatingFuture) { details =>
          details.map(_.state) shouldBe Some(TaskCurrentState.Running())
        }
      }
  }
  "WorkerPool with real factory" should {}

  class MockFetch extends Fetch[String, Int] {
    var requested: Vector[String] = Vector()
    def make(url: String)(implicit as: ActorSystem[_]): Source[Int, _] = {
      requested = requested :+ url
      Source(List(1, 2, 3))
    }
  }
  class MockSaver extends Saver[Long, String, Int] {
    var unmade: Vector[(String, TaskFinishReason)] = Vector.empty
    var saved: Map[String, Vector[Int]] = Map.empty
    def make(file: String): Sink[Int, _] =
      Sink.foreach { n =>
        val existing = saved.getOrElse(file, Vector.empty)
        saved = saved + (file -> (existing :+ n))
      }
    def unmake(file: String, reason: TaskFinishReason): Unit =
      unmade = unmade :+ (file, reason)
    def target(taskId: Long): String = taskId.toString()
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
  class MockFactory extends WorkerFactory[Long, Unit, String] {
    var workers: Map[Long, MockWorker] = Map.empty
    def createWorker(
        taskId: Long,
        url: Unit,
        result: String,
        onDone: Long => Unit,
        onFailure: Long => Unit
    )(implicit as: ActorSystem[_]): Worker = {
      val worker = new MockWorker(onDone, onFailure)
      workers = workers + (taskId -> worker)
      worker
    }
  }
}