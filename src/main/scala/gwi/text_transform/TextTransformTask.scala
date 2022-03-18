package gwi.text_transform

import gwi.{Status, TaskState}

import java.io.{InputStream, OutputStream}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.io.Source
import scala.util.{Failure, Success, Using}

abstract class StatefullTask[S, R](initialState: S) {
  private var taskState = initialState
  protected val interrupted = new AtomicBoolean(false)

  def getTaskState(): S = this.synchronized {taskState}
  def setTaskState(state: S): Unit = this.synchronized {taskState = state}
  def doTask(): R
  def interruptTask(): Unit = {
    interrupted.set(true)
  }
  protected def onTaskInterrupted(): Unit
}

abstract class TextTransformTask extends StatefullTask[TaskState, Unit](TaskState.initial) {

  private var lastUpdate: Long = 0
  protected var curLineIndex: Long = 0
  private var linesInCurrentInterval: Long = 0 // for progress calculation

  def processLine(line: String, destination: OutputStream): Unit

  override def doTask(): Unit = {
    setTaskState(TaskState(status = Status.Running.toString, 0))
    lastUpdate = System.nanoTime()

    try {
      Using.Manager { use =>
        val in  = use(getInputStream())
        val out = use(getOutputStream)

        val it = Source.fromInputStream(in).getLines()
        while(it.hasNext) {
          processLine(it.next(), out)
          onProgress
        }
      } match {
        case Success(_) =>
          println("task done")
          setTaskState(TaskState(status = Status.Done.toString, 0, resultUrl = Some(getResultUrl)))
        case Failure(ex) =>
          setTaskState(TaskState(status = Status.Failed.toString, 0))
          println("task failed: " + ex)
      }
    } finally {
      if(interrupted.get()) {
        println("task interrupted")
        onTaskInterrupted()
      }
    }
  }


  protected def getInputStream(): InputStream
  protected def getOutputStream: OutputStream
  protected def getResultUrl: String

  private def onProgress: Unit = {
    curLineIndex += 1
    println("processing line #" + curLineIndex)

    linesInCurrentInterval += 1

    val now = System.nanoTime()
    if (TimeUnit.NANOSECONDS.toSeconds(now - lastUpdate) > 2) {

      setTaskState(TaskState(status = Status.Running.toString, avgLinesProcessed = linesInCurrentInterval.toDouble / 2, resultUrl = None))

      lastUpdate = now
      linesInCurrentInterval = 0
    }
  }
}

