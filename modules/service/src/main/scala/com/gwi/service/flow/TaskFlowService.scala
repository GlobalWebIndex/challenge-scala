package com.gwi.service.flow

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.ByteString
import com.google.inject.{Inject, Singleton}
import com.gwi.service.config.AppConfig
import com.gwi.service.flow.converter.CsvConverter
import spray.json.JsValue

@Singleton
class TaskFlowService @Inject() (config: AppConfig)(implicit val materializer: Materializer) {

  def createConsumeCsvFlowFlow(): Flow[ByteString, (JsValue, Long), NotUsed] = {
    CsvConverter.convertLineFlow
      .buffer(config.backPressure.bufferSize, OverflowStrategy.backpressure)
      .async
  }
}
