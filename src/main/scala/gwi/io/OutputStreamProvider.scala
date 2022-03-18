package gwi.io

import java.io.{FileOutputStream, OutputStream}
import java.nio.file.Files
import java.nio.file.Paths

trait OutputStreamProvider {
  def getOutputStream(fileName: String): OutputStream
}

class LocalFileOutputStreamProvider extends OutputStreamProvider {
  override def getOutputStream(fileName: String): OutputStream = {
    ensureDirectoryCreated()

    new FileOutputStream(fileName)
  }

  private def ensureDirectoryCreated() = {
    val fileName = "data"

    val path = Paths.get(fileName)

    if (!Files.exists(path)) {
      Files.createDirectory(path)
      System.out.println("Output Directory created")
    }
  }
}
