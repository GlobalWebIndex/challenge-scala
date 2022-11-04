package com.gwi.service.flow.converter

import akka.NotUsed
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl.{Flow, GraphDSL, Source, Unzip, Zip, ZipWith}
import akka.util.ByteString
import spray.json._
import DefaultJsonProtocol._
import akka.stream.FlowShape
import com.typesafe.scalalogging.LazyLogging

object CsvConverter extends LazyLogging {

  def convertLineFlow: Flow[ByteString, (JsValue, Long), NotUsed] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val simple = builder.add(
        CsvParsing.lineScanner().via(CsvToMap.toMapAsStrings()).map(_.toJson).map(j => (j, System.currentTimeMillis()))
      )
      FlowShape(simple.in, simple.out)
//      val csvParseByteStringFlowShape =
//        builder.add(CsvParsing.lineScanner().map(byteStringList => (byteStringList, System.currentTimeMillis())))
//      val unzipLinesAndTimerShape = builder.add(Unzip[List[ByteString], Long])
//      val mapperShape = builder.add(
//        CsvToMap
//          .toMapAsStrings()
//          .map(a => {
//            logger.info(s"mapper $a")
//            a.toJson
//          })
//      )
//      val zipShape = builder.add(Zip[JsValue, Long])
////
//      csvParseByteStringFlowShape.out ~> unzipLinesAndTimerShape.in
//      unzipLinesAndTimerShape.out0.map(l => {
//        logger.info(s"unzpi 0 $l")
//        l
//      }) ~> mapperShape.in
//      mapperShape.out.map(l => {
//        logger.info(s"mapper $l")
//        l
//      }) ~> zipShape.in0
//      unzipLinesAndTimerShape.out1 ~> zipShape.in1
//
//      FlowShape(csvParseByteStringFlowShape.in, zipShape.out)
    })
  }
}
