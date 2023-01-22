name := "challenge"
organization := "challenge"

version := "1.0"

val AkkaVersion = "2.7.0"
val AkkaHttpVersion = "10.4.0"

val circeVersion = "0.14.1"

lazy val root = (project in file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(pool, app)
lazy val pool = (project in file("libs/pool"))
  .settings(
    name := "pool",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.15" % Test
    )
  )
lazy val app = (project in file("app"))
  .settings(
    name := "app",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "com.lightbend.akka" %% "akka-stream-alpakka-csv" % "5.0.0",
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
      "de.heikoseeberger" %% "akka-http-circe" % "1.39.2",
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.15" % Test
    )
  )
  .dependsOn(pool)

ThisBuild / scalaVersion := "2.13.10"

ThisBuild / scalafixScalaBinaryVersion := "2.13"
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
ThisBuild / scalafixDependencies += "com.github.vovapolu" %% "scaluzzi" % "0.1.23"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

ThisBuild / scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-Xfatal-warnings",
  "-Wunused:imports"
)

Compile / console / scalacOptions -= "-Wunused:imports"

ScalaUnidoc / unidoc / scalacOptions ++= Seq(
  "-doc-root-content",
  "app/rootdoc.txt"
)
