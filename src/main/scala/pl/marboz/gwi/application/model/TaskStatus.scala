package pl.marboz.gwi.application.model

/**
 * @author <a href="mailto:marboz85@gmail.com">Marcin Bozek</a>
 * Date: 20.05.2023 20:29
 */
object TaskStatus extends Enumeration {

  val SCHEDULED,RUNNING,DONE,FAILED,CANCELED,UNKNOWN = Value

}