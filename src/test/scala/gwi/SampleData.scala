package gwi

import akka.http.scaladsl.model.Uri
import com.gwi.api.TaskState
import com.gwi.execution.Task

import java.util.UUID

trait SampleData {

  protected val sampleTask: Task = Task(
    id = UUID.fromString("0b406535-21c8-4bcf-b79b-41ab7a01c264"),
    csvUri = Uri("http://test.com"),
    linesProcessed = 1000,
    state = TaskState.Done
  )

}
