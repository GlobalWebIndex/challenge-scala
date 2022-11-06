package com.gwi.service.flow.converter

import akka.NotUsed
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl.{Flow, GraphDSL, Unzip, Zip}
import akka.util.ByteString
import spray.json._
import DefaultJsonProtocol._
import akka.stream.FlowShape
import com.typesafe.scalalogging.LazyLogging

object CsvConverter extends LazyLogging {

  val convertLineFlow: Flow[ByteString, (JsValue, Long), NotUsed] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val lineScannerShape = builder.add(CsvParsing.lineScanner().map(byteString => (byteString, System.nanoTime())))
      val unzipShape = builder.add(Unzip[List[ByteString], Long])
      val processShape = builder.add(CsvToMap.toMapAsStrings().map(_.toJson))
      val bufferShape = builder.add(Flow[Long].drop(1)) // we need to drop first timer element
      val zipShape = builder.add(Zip[JsValue, Long])
      val getProcessTimeShape = builder.add(Flow[(JsValue, Long)].map { case (jsonLine, startTime) =>
        (jsonLine, System.nanoTime() - startTime)
      })

      lineScannerShape.out ~> unzipShape.in
      unzipShape.out0 ~> processShape ~> zipShape.in0
      unzipShape.out1 ~> bufferShape ~> zipShape.in1
      zipShape.out ~> getProcessTimeShape

      FlowShape(lineScannerShape.in, getProcessTimeShape.out)
    })
  }
}
