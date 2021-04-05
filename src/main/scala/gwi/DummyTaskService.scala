package gwi

import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.Done
import scala.concurrent.Future

class DummyTaskService extends TaskService {
  override def create(source: Uri): Future[TaskId] =
    Future.failed(BadRequestError("invalid request"))

  override def getAll: Future[List[Task]] =
    Future.failed(BadRequestError("invalid request"))

  override def get(id: TaskId): Future[Source[Task, _]] =
    Future.failed(BadRequestError("invalid request"))

  override def cancel(id: TaskId): Future[Done] =
    Future.failed(BadRequestError("invalid request"))

  override def result(id: TaskId): Future[Source[ByteString, _]] =
    Future.failed(BadRequestError("invalid request"))
}
