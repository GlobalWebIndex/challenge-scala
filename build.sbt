name := """challenge"""
organization := "challenge"

version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))

scalaVersion := "2.13.10"

val AkkaVersion = "2.7.0"
val AkkaHttpVersion = "10.4.0"
val AlpakkaVersion = "5.0.0"

val circeVersion = "0.14.1"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.11"
libraryDependencies += "com.lightbend.akka" %% "akka-stream-alpakka-csv" % AlpakkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion
libraryDependencies += "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion
libraryDependencies += "de.heikoseeberger" %% "akka-http-circe" % "1.39.2"
libraryDependencies += "io.circe" %% "circe-core" % circeVersion
libraryDependencies += "io.circe" %% "circe-generic" % circeVersion
libraryDependencies += "io.circe" %% "circe-parser" % circeVersion

ThisBuild / scalafixScalaBinaryVersion := "2.13"
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
ThisBuild / scalafixDependencies += "com.github.vovapolu" %% "scaluzzi" % "0.1.23"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-Xfatal-warnings",
  "-Wunused:imports"
)

Compile / console / scalacOptions -= "-Wunused:imports"
