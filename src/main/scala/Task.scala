package com.gwi.karelsk

import akka.actor.typed.ActorRef
import spray.json.DefaultJsonProtocol._
import spray.json._

import java.net.URI
import java.nio.file.Path

sealed trait Task {
  def id: Task.Id
  def isTerminal: Boolean = false
  def cancel: Option[Task]
  protected def stateString: String = getClass.getSimpleName.toUpperCase
  protected def jsonFields(resultLoc: Task.Id => String): Seq[JsField] =
    Seq("id" -> id.toJson, "state" -> stateString.toJson)
  def toJson(implicit resultLoc: Task.Id => String): JsValue = JsObject(jsonFields(resultLoc): _*)
}

object Task {
  type Id = Long
  type Progress = (Int, Long)

  val initialId: Id = 0
  implicit class IdOps(val id: Id) extends AnyVal {
    def next: Id = id + 1
  }

  final case class Scheduled private(id: Task.Id, uri: URI) extends Task {
    def run(worker: ActorRef[_], result: Path): Running = Running(id, (0, 0L), result, worker)
    def cancel: Option[Task] = Some(Canceled(id, (0, 0)))
    override protected def jsonFields(resultLoc: Id => String): Seq[JsField] =
      super.jsonFields(resultLoc) :+ ("source" -> uri.toString.toJson)
  }

  final case class Running private(id: Task.Id, progress: Progress, result: Path, worker: ActorRef[_]) extends Task {
    def update(p: Progress): Running = copy(progress = p)
    def done: Done = Done(id, progress, result)
    def cancel: Option[Task] = Some(Canceled(id, progress))
    def fail(cause: Throwable): Failed = Failed(id, progress, cause)
    override protected def jsonFields(resultLoc: Id => String): Seq[JsField] =
      super.jsonFields(resultLoc) ++ progressFields(progress)
  }

  trait Terminal extends Task {
    override final def isTerminal = true
    override final def cancel: Option[Task] = None
    def progress: Progress
    override protected def jsonFields(resultLoc: Id => String): Seq[JsField] =
      super.jsonFields(resultLoc) ++ progressFields(progress)
  }

  final case class Done private(id: Task.Id, progress: Progress, result: Path) extends Terminal {
    override protected def jsonFields(resultLoc: Id => String): Seq[JsField] =
      super.jsonFields(resultLoc) :+ ("result" -> resultLoc(id).toJson)
  }

  final case class Failed private(id: Task.Id, progress: Progress, cause: Throwable) extends Terminal
  final case class Canceled private(id: Task.Id, progress: Progress) extends Terminal

  def apply(id: Id, uri: URI): Scheduled = Scheduled(id, uri)

  private def progressFields(progress: Progress): Seq[JsField] = {
    val (lines, elapsed) = progress
    val rate = if (elapsed == 0) 0.0f else lines.toFloat * 1000 / elapsed
    Seq("lines" -> lines.toJson, "rate" -> rate.toJson)
  }
}
