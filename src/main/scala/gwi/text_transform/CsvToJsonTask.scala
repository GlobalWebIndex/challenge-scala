package gwi.text_transform
import gwi.AppContext

import java.io.{File, InputStream, OutputStream}
import java.nio.charset.Charset

class CsvToJsonTask(csvUrl: String, outputFileName: String, appContext: AppContext) extends TextTransformTask {
  var header = scala.collection.mutable.Seq[String]()

  override def processLine(line: String, destination: OutputStream): Unit = {
    if(curLineIndex == 0) {
      header = line.split(",")
    } else {
      val props = header
        .zip(line.split(","))
        .map[String](keyValue => s"""\"${keyValue._1.trim}\":\"${keyValue._2.trim}\"""" )
        .mkString(",")

      val json = s"""{$props}\n"""
      destination.write(json.getBytes(Charset.forName("UTF-8")))
    }
  }

  override protected def getInputStream(): InputStream = appContext.inputStreamProvider.getInputStream(csvUrl)

  override protected def getOutputStream: OutputStream = {
    appContext.outputStreamProvider.getOutputStream(outputFileName)
  }

  override protected def getResultUrl: String = s"${appContext.persistanceBaseUrl}/$outputFileName"

  override def onTaskInterrupted(): Unit =
  {
    try {
      new File(outputFileName).delete()
    } catch {
      case ex : Exception => println("failed to delete file: ", ex)
    }
  }

}
