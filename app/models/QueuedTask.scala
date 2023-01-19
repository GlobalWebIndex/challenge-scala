package models

import java.io.File

final case class QueuedTask(taskId: String, url: String, result: File)
