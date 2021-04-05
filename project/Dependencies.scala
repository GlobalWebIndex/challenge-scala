import sbt._

object Dependencies {
  val akkaVersion = "2.6.13"
  val akkaHttpVersion = "10.2.4"
  val circeVersion = "0.13.0"

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaActorTyped = "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
  val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion % Runtime

  val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
  val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val akkaHttpCirce = "de.heikoseeberger" %% "akka-http-circe" % "1.36.0"

  val alpakkaCsv = "com.lightbend.akka" %% "akka-stream-alpakka-csv" % "2.0.2"

  val circeCore = "io.circe" %% "circe-core" % circeVersion
  val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  val circeGenericExtras = "io.circe" %% "circe-generic-extras" % circeVersion

  val shapeless = "com.chuusai" %% "shapeless" % "2.3.3"
  val catsCore = "org.typelevel" %% "cats-core" % "2.1.0"

  val slf4j = "org.slf4j" % "slf4j-api" % "1.7.30"
  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3" % Runtime

  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.7" % Test
  val scalaMock = "org.scalamock" %% "scalamock" % "5.1.0" % Test

  val akkaActorTypedTestkit = "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test
  val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
  val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test

  val deps = Seq(
    akkaActor,
    akkaActorTyped,
    akkaStreams,
    akkaSlf4j,
    akkaHttpCore,
    akkaHttp,
    akkaHttpCirce,
    alpakkaCsv,
    circeCore,
    circeGeneric,
    circeGenericExtras,
    shapeless,
    catsCore,
    slf4j,
    logback,
    scalaTest,
    scalaMock,
    akkaActorTypedTestkit,
    akkaStreamTestkit,
    akkaHttpTestkit
  )
}
