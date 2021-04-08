package pl.datart.csvtojson.model

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import pl.datart.csvtojson.model.TaskState._

import java.util.Locale

class TaskStateTest extends AsyncFunSpec with Matchers {
  describe("Task state") {
    it("should have correct string representation") {
      Set[TaskState](
        Scheduled,
        Running,
        Canceled,
        Failed,
        Done
      ).forall { state =>
        state.asString ===
          state.getClass.getSimpleName.replaceAll("\\$", "").toUpperCase(Locale.getDefault)
      } shouldBe true
    }
  }
}
