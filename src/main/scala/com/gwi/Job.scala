package com.gwi

import java.util.UUID
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.http.scaladsl.model.Uri
import com.github.tototoshi.csv._
import scala.io.Source
import scala.concurrent.duration._
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import java.util.Deque
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.File
import spray.json._
import DefaultJsonProtocol._ 
object Job {
// Definition of the a build job and its possible status values
  sealed trait Status
  object Scheduled extends Status
  object Running   extends Status
  object Done      extends Status
  object Failed    extends Status
  object Canceled  extends Status

  sealed trait Command
  case class GetStatus(replyTo: ActorRef[Option[TaskStatus]]) extends Command
  case object Start                                           extends Command
  case object ProcessLine                                     extends Command
  case object Cancel                                          extends Command

  sealed trait Response
  final case class TaskStatus(id: Int, linesCnt: Int, avgLinesCnt: Int, status: Job.Status, target: Uri)
      extends Response



  private case class State(
    id: Int,
    sourceUri: Uri,
    targetUri: Uri,
    reader: CSVReader,
    iterator: Iterator[Map[String, String]],
    bufferedWriter: BufferedWriter,
    linesCnt: Int,
    startTime: Long,
    endTime: Option[Long],
    status: Status,
    taskRepository: ActorRef[TaskRepository.Command]
  )
  def apply(id: TaskRepository.Id, sourceUri: Uri, taskRepository: ActorRef[TaskRepository.Command]) = {
    val start     = System.currentTimeMillis()
    val targetUri = Uri(s"/tmp/$id.json")
    val bw        = new BufferedWriter(new FileWriter(new File(targetUri.toString()),true))
    val source    = Source.fromURL(sourceUri.toString())
    val reader    = CSVReader.open(source)
    val iterator  = reader.iteratorWithHeaders
    processing(
      State(id, sourceUri, targetUri, reader, iterator,bw, 0, System.currentTimeMillis(), None, Scheduled, taskRepository)
    )
  }

  def processing(state: State): Behavior[Job.Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case GetStatus(replyTo) =>
          val diff = (state.endTime.getOrElse(System.currentTimeMillis()) - state.startTime) / 1000
          val avg  = if (diff.toInt == 0) state.linesCnt else state.linesCnt / diff.toInt
          replyTo ! Some(TaskStatus(state.id, state.linesCnt, avg, state.status, state.targetUri))
          Behaviors.same

        case Start =>
          if (state.status == Scheduled || state.status == Canceled) {
            state.bufferedWriter.write("[\n")
            context.self ! ProcessLine
            processing(state.copy(status = Running))
          } else Behaviors.same
        case Cancel =>
          if (state.status == Running || state.status == Scheduled) {
            processing(state.copy(status = Canceled, endTime = Some(System.currentTimeMillis())))
          } else Behaviors.same
        case ProcessLine =>
          if (state.status == Running && state.iterator.hasNext) {
            val line = state.iterator.next
            val comma = if (state.linesCnt ==0) "" else ","
            state.bufferedWriter.write(comma + line.toJson.prettyPrint)
            context.self ! ProcessLine
            processing(
              state.copy(linesCnt = state.linesCnt + 1, status = Running, endTime = Some(System.currentTimeMillis()))
            )
          } else {
            state.taskRepository ! TaskRepository.JobDone(state.id, state.targetUri)
            state.reader.close()
            state.bufferedWriter.write("\n]")
            state.bufferedWriter.close()
            processing(state.copy(status = Done, endTime = Some(System.currentTimeMillis())))
          }
      }
    }

}
