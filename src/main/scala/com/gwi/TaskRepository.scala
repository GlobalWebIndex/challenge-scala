package com.gwi

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.Uri
import java.util.UUID
import scala.collection.immutable.Queue
import scala.annotation.meta.setter

object TaskRepository {
  type Id = Int
  final case class Task(
    uri: Uri
  )

  // Trait defining successful and failure responses
  sealed trait Response
  // final case class OK[A](payload: A)  extends Response[A]
  // final case class KO[A](reason: String) extends Response[A]

  final case class TaskCreated(id: Id) extends Response
  final case class TaskDeleted(id: Id) extends Response

  sealed trait Command
  final case class CreateTask(task: Task, replyTo: ActorRef[TaskCreated])         extends Command
  final case class GetTaskById(id: Id, replyTo: ActorRef[Option[Job.TaskStatus]]) extends Command
  final case class GetTasks(replyTo: ActorRef[List[Id]])                          extends Command
  final case class DeleteTask(id: Id, replyTo: ActorRef[TaskDeleted])             extends Command
  final case class GetJsons(replyTo: ActorRef[Map[String, Uri]])                  extends Command
  final case object Schedule                                                      extends Command
  final case class JobDone(id: Id, targetUri: Uri)                                extends Command



  def apply() = state(0, Map.empty[Id, ActorRef[Job.Command]], Queue.empty[Id], 0, Map.empty[Id, Uri])

  def state(
    currentId: Id,
    tasks: Map[Id, ActorRef[Job.Command]],
    waiting: Queue[Id],
    running: Int,
    readyUris: Map[Id, Uri]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      context.self ! Schedule

      Behaviors.receiveMessage {
        case CreateTask(task, replyTo) =>
          val taskJob = context.spawn(Job(currentId, task.uri, context.self), s"task-$currentId")
          replyTo ! TaskCreated(currentId)
          state(currentId + 1, tasks + (currentId -> taskJob), waiting.enqueue(currentId), running, readyUris)

        case GetTaskById(id, replyTo) =>
          val job = tasks.get(id)
          job match {
            case None         => replyTo ! None
            case Some(jobRef) =>  jobRef ! Job.GetStatus(replyTo)
          }
          Behaviors.same
        case GetTasks(replyTo) =>
          replyTo ! tasks.keySet.toList
          Behaviors.same

        case DeleteTask(id, replyTo) =>
          replyTo ! TaskDeleted(id)
          state(currentId, tasks - id, waiting, running, readyUris)

        case Schedule =>
          if (waiting.nonEmpty && running < 2) {
            val (id, newWaiting) = waiting.dequeue
            tasks.get(id).foreach(ref => ref ! Job.Start)
            state(currentId, tasks, newWaiting, running + 1, readyUris)
          } else {
            Behaviors.same
          }
        case JobDone(id, uri) =>
          if (running > 0) {
            state(currentId, tasks, waiting, running - 1, readyUris + (id -> uri))
          } else {
            Behaviors.same
          }
        case GetJsons(replyTo) =>
          replyTo ! readyUris.map { case (k, v) => k.toString() -> v }
          Behaviors.same

      }
    }

}
