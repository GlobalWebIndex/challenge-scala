package com.gwi.service.dto

object GetJsonLinesError extends Enumeration {
  type GetJsonLinesError = Value
  val NOT_FOUND: GetJsonLinesError = Value("Not Found")
  val NOT_DONE_STATE: GetJsonLinesError = Value("Not Done State")
}
