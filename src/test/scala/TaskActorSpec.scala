import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.stream.IOResult
import akka.stream.scaladsl.Flow
import com.example.TaskActor.{ListTasksResponse, Task, TaskActorResponse}
import com.example.{TaskActor, TaskToFileFlow}
import org.scalatest.wordspec.AnyWordSpecLike

class TaskActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  object MockFlow extends TaskToFileFlow {
    override val flow: Flow[TaskActor.Task, IOResult, NotUsed] = Flow[Task]
      .map{ _ => IOResult.apply(1) }
  }

  "TaskActor" should {

    val taskActor = testKit.spawn(TaskActor(MockFlow), name = "test")
    val listTasksProbe = testKit.createTestProbe[ListTasksResponse]()
    val taskCreationProbe = testKit.createTestProbe[Task]()
    val cancelTaskProbe = testKit.createTestProbe[TaskActorResponse]()

    "Return list of empty tasks" in {

      // Empty Tasks
      taskActor ! TaskActor.ListTasks(listTasksProbe.ref)
      listTasksProbe.expectMessage(ListTasksResponse(Seq.empty))
    }

    "Create and run tasks if possible" in {
      // Create
      taskActor ! TaskActor.CreateTask("csvUri1", taskCreationProbe.ref)
      taskCreationProbe.expectMessage(Task(1, "csvUri1", TaskActor.RUNNING))

      taskActor ! TaskActor.CreateTask("csvUri2", taskCreationProbe.ref)
      taskCreationProbe.expectMessage(Task(2, "csvUri2", TaskActor.RUNNING))
    }

    "Schedule task if max limit of running is reached" in {
      // Should not run the third one immediately
      taskActor ! TaskActor.CreateTask("csvUri3", taskCreationProbe.ref)
      taskCreationProbe.expectMessage(Task(3, "csvUri3", TaskActor.SCHEDULED))

      taskActor ! TaskActor.ListTasks(listTasksProbe.ref)
      listTasksProbe.expectMessage(ListTasksResponse(
        List(
          Task(1, "csvUri1", TaskActor.RUNNING, None),
          Task(2, "csvUri2", TaskActor.RUNNING, None),
          Task(3, "csvUri3", TaskActor.SCHEDULED, None))))
    }

    "Cancel a task and immediately run next scheduled" in {
      taskActor ! TaskActor.CancelTask(2, cancelTaskProbe.ref)
      cancelTaskProbe.expectMessage(TaskActorResponse("Task 2 was canceled"))

      taskActor ! TaskActor.ListTasks(listTasksProbe.ref)
      listTasksProbe.expectMessage(ListTasksResponse(
        List(
          Task(1,"csvUri1",TaskActor.RUNNING,None),
          Task(2,"csvUri2",TaskActor.CANCELED,None),
          Task(3,"csvUri3",TaskActor.RUNNING,None))))
    }

  }
}
