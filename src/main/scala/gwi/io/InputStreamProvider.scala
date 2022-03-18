package gwi.io

import java.io.InputStream
import java.net.{HttpURLConnection, URL}

trait InputStreamProvider {
  def getInputStream(uri: String): InputStream
}

class UrlInputStreamProvider() extends InputStreamProvider {

  val connectTimeout = 5000
  val readTimeout = 5000

  override def getInputStream(url: String): InputStream = {
    val u = new URL(url)
    val conn = u.openConnection.asInstanceOf[HttpURLConnection]
    HttpURLConnection.setFollowRedirects(false)
    conn.setConnectTimeout(connectTimeout)
    conn.setReadTimeout(readTimeout)
    conn.setRequestMethod("GET")
    conn.connect
    conn.getInputStream
  }
}
