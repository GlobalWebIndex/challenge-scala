import sbt._

object Dependencies {

  val akkaHttpVersion = "10.2.6"
  val akkaVersion = "2.6.16"
  val circeVersion = "0.14.1"

  val akkaHttpDeps: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-caching" % akkaHttpVersion
  )

  val akkaDeps: Seq[ModuleID] = Seq(
    "com.lightbend.akka" %% "akka-stream-alpakka-csv" % "3.0.3",
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  )

  val jsonDeps: Seq[ModuleID] = Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-generic-extras" % circeVersion,
    "de.heikoseeberger" %% "akka-http-circe" % "1.38.2"
  )

  val testDeps: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
    "org.typelevel" %% "discipline-scalatest" % "2.1.5" % Test,
    "org.scalamock" %% "scalamock" % "5.1.0" % Test
  )

  val loggingDeps = Seq("org.slf4j" % "slf4j-api" % "1.7.32", "ch.qos.logback" % "logback-classic" % "1.2.6")

  val all: Seq[ModuleID] = akkaHttpDeps ++ akkaDeps ++ jsonDeps ++ loggingDeps ++ testDeps

}
