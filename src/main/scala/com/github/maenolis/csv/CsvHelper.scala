package com.github.maenolis.csv

import spray.json.{DefaultJsonProtocol, JsValue, JsonWriter}

object CsvHelper extends DefaultJsonProtocol {

  def toJson(map: Map[String, String])(implicit
      jsWriter: JsonWriter[Map[String, String]]
  ): JsValue = jsWriter.write(map)

}
