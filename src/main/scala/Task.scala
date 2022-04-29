package com.gwi.karelsk

import spray.json.DefaultJsonProtocol._
import spray.json._

import java.net.URI
import java.nio.file.Path

sealed trait Task {
  import Task._

  def id: Id
  def isTerminal: Boolean
  def cancel: Option[Canceled]
  def progress: Progress
  protected def stateString: String = getClass.getSimpleName.toUpperCase
  protected def jsonFields(resultLoc: Id => String): Seq[JsField] =
    Seq("id" -> id.toJson, "state" -> stateString.toJson)
  def toJson(implicit resultLoc: Id => String): JsValue = JsObject(jsonFields(resultLoc): _*)
}

object Task {
  type Id = Long
  type Progress = (Int, Long)

  val initialId: Id = 0
  implicit class IdOps(val id: Id) extends AnyVal {
    def next: Id = id + 1
  }

  trait Cancelable extends Task {
    override final def isTerminal = false
  }

  final case class Scheduled private(id: Id, uri: URI) extends Cancelable {
    override def progress: (Int, Id) = (0, 0L)
    def run(result: Path): Running = Running(id, (0, 0L), result)
    def cancel: Option[Canceled] = Some(Canceled(id, progress))
    def fail(cause: Throwable): Failed = Failed(id, progress, cause)
    override protected def jsonFields(resultLoc: Id => String): Seq[JsField] =
      super.jsonFields(resultLoc) :+ ("source" -> uri.toString.toJson)
  }

  final case class Running private(id: Id, progress: Progress, result: Path) extends Cancelable {
    def advanceTo(p: Progress): Running = copy(progress = p)
    def cancel: Option[Canceled] = Some(Canceled(id, progress))
    def finish: Done = Done(id, progress, result)
    def fail(cause: Throwable): Failed = Failed(id, progress, cause)
    override protected def jsonFields(resultLoc: Id => String): Seq[JsField] =
      super.jsonFields(resultLoc) ++ progressFields(progress)
  }

  trait Terminal extends Task {
    override final def isTerminal = true
    override final def cancel: Option[Canceled] = None
    override protected def jsonFields(resultLoc: Id => String): Seq[JsField] =
      super.jsonFields(resultLoc) ++ progressFields(progress)
  }

  final case class Canceled private(id: Id, progress: Progress) extends Terminal

  final case class Done private(id: Id, progress: Progress, result: Path) extends Terminal {
    override protected def jsonFields(resultLoc: Id => String): Seq[JsField] =
      super.jsonFields(resultLoc) :+ ("result" -> resultLoc(id).toJson)
  }

  final case class Failed private(id: Id, progress: Progress, cause: Throwable) extends Terminal {
    override protected def jsonFields(resultLoc: Id => String): Seq[JsField] =
      super.jsonFields(resultLoc) :+ ("cause" -> cause.toString.toJson)
  }

  def apply(id: Id, uri: URI): Scheduled = Scheduled(id, uri)

  private def progressFields(progress: Progress): Seq[JsField] = {
    val (lines, elapsed) = progress
    val rate = if (elapsed == 0) 0.0f else lines.toFloat * 1000 / elapsed
    Seq("lines" -> lines.toJson, "rate" -> rate.toJson)
  }
}
