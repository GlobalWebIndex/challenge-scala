package cz.vlasec.gwi.csvimport

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object Routes {
  private val task: Route =
    pathPrefix("task") {
      concat(
        pathEnd {
          concat(
            get {
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "All running tasks"))
            },
            post {
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Created a new task"))
            }
          )
        },
        path(RemainingPath) { id =>
          concat(
            get {
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Status of task $id"))
            },
            delete {
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Deleted task $id"))
            }
          )
        }
      )
    }

  private val json: Route =
    pathPrefix("json") {
      concat(
        pathEnd {
          get {
            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "List of all JSON files"))
          }
        },
        path(RemainingPath) { filename =>
          get {
            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Contents of file $filename"))
          }
        }
      )
    }

  val routes: Route = Route.seal(concat(task, json))
}
