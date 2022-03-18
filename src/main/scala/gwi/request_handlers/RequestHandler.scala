package gwi.request_handlers


final case class NotFoundException(private val message: String = "resource not found")
  extends Exception(message)

trait RequestHandler[IN,OUT] {
  def handle(params: IN): OUT
}
