import sbt.Keys._
import sbt._

val ProjectScalaVersion = "2.13.8"

//Test / parallelExecution := true

lazy val database = project
  .in(file("modules/database"))
  .settings(scalaVersion := ProjectScalaVersion)
  .settings(libraryDependencies ++= Dependencies.logging)
  .settings(libraryDependencies ++= Seq(Dependencies.guice, Dependencies.akkaActor, Dependencies.akkaStream))
  .settings(libraryDependencies ++= Dependencies.slick)
  .settings(libraryDependencies ++= Seq(Dependencies.scalaTest, Dependencies.akkaStreamsTestKit))

lazy val service = project
  .in(file("modules/service"))
  .settings(scalaVersion := ProjectScalaVersion)
  .dependsOn(database)
  .settings(libraryDependencies ++= Dependencies.logging)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.guice,
      Dependencies.akkaActor,
      Dependencies.akkaStream,
      Dependencies.alpakkaCsv,
      Dependencies.akkaHttp,
      Dependencies.akkaSpray
    )
  )
  .settings(libraryDependencies ++= Seq(Dependencies.scalaTest, Dependencies.mockito, Dependencies.akkaStreamsTestKit))

lazy val server = project
  .in(file("modules/server"))
  .settings(scalaVersion := ProjectScalaVersion)
  .dependsOn(service)
  .settings(libraryDependencies ++= Dependencies.logging)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.guice,
      Dependencies.akkaActor,
      Dependencies.akkaStream,
      Dependencies.akkaHttp,
      Dependencies.akkaSpray
    )
  )
  .settings(libraryDependencies += Dependencies.scalaTest)
  .settings(Compile / mainClass := Some("com.gwi.server.MainApp"))
//.settings(run / mainClass := Some("com.gwi.server.HttpServer"))
