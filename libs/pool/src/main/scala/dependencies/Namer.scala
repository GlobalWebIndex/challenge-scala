package pool.dependencies

/** Customizable utilities to deal with task identifiers */
trait Namer[ID] {

  /** Creates a unique identifier for the task */
  def makeTaskId(): ID
}
