package pool.dependencies

trait Namer[ID] {
  def makeTaskId(): ID
}
