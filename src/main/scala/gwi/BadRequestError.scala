package gwi

final case class BadRequestError(msg: String) extends Throwable
