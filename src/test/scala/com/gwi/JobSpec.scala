package com.gwi

import akka.actor.testkit.typed.scaladsl.ActorTestKit

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import akka.http.scaladsl.model.Uri
import scala.io.Source

class JobSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers {
  val testKit                   = ActorTestKit()
  override def afterAll(): Unit = testKit.shutdownTestKit()

  "Job" must {
    "be spawned with status SCHEDULED" in {
      val uri    = Uri(getClass.getResource("/world_population.csv").toURI().toString())
      val probe  = testKit.createTestProbe[Option[Job.TaskStatus]]()
      val probe1 = testKit.createTestProbe[TaskRepository.Command]()
      val id = 1
      val job    = testKit.spawn(Job(id, uri, probe1.ref), "job-1")

      job ! Job.GetStatus(probe.ref)
      val message = probe.receiveMessage()
      assertResult(Job.Scheduled)(message.get.status)
      assertResult(s"/tmp/$id.json")(message.get.target.toString())
    }
  }
}
