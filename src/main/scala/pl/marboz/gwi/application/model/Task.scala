package pl.marboz.gwi.application.model

import org.http4s.Uri

import java.util.UUID

/**
 * @author <a href="mailto:marboz85@gmail.com">Marcin Bozek</a>
 * Date: 20.05.2023 20:28
 */
final case class Task (linesProcessedAmount: Int, totalLinesAmount: Int, uuid: UUID, status: TaskStatus.Value, uri: Option[Uri])