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

  private val csvLineScannerFlow: Flow[ByteString, (List[ByteString], Long), NotUsed] =
    CsvParsing.lineScanner().map(byteString => (byteString, System.nanoTime()))

  private val csvTransformFlow: Flow[(List[ByteString], Long), (JsValue, Long), NotUsed] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val unzipShape = builder.add(Unzip[List[ByteString], Long])
      val processShape = builder.add(CsvToMap.toMapAsStrings().map(_.toJson))
      val bufferShape = builder.add(Flow[Long].drop(1)) // we need to drop first timer element
      val zipShape = builder.add(Zip[JsValue, Long])
      val getProcessTimeShape = builder.add(Flow[(JsValue, Long)].map { case (jsonLine, startTime) =>
        (jsonLine, ((System.nanoTime() - startTime) / Math.pow(10, 6)).toLong) // convert to millis
      })

      unzipShape.out0 ~> processShape ~> zipShape.in0
      unzipShape.out1 ~> bufferShape ~> zipShape.in1
      zipShape.out ~> getProcessTimeShape

      FlowShape(unzipShape.in, getProcessTimeShape.out)
    })
  }

  val convertLineFlow: Flow[ByteString, (JsValue, Long), NotUsed] = csvLineScannerFlow.async.via(csvTransformFlow)
}
