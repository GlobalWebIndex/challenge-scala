package com.gwi.service.converter

import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Sink}
import akka.testkit.TestKit
import com.gwi.service.flow.converter.CsvConverter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AsyncWordSpecLike
import spray.json.JsValue

import java.nio.file.Paths

class CsvConverterTest
    extends TestKit(ActorSystem("CsvConverterTestSpec"))
    with AsyncWordSpecLike
    with BeforeAndAfterAll {

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  "csv converter" should {
    "correctly parse a file" in {
      val url = getClass.getResource("/Lottery_Powerball_Winning_Numbers__Beginning_2010.csv")
      val sourceFilePath = Paths.get(url.getPath)
      val result = FileIO
        .fromPath(sourceFilePath)
        .via(CsvConverter.convertLineFlow)
        .runWith(Sink.collection[(JsValue, Long), List[(JsValue, Long)]])
      result.map(flowList => {
        assert(flowList.size == 1388)
      })
    }
  }
}
