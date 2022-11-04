import sbt.Keys._
import sbt._

val ProjectScalaVersion = "2.13.8"

scalafmtOnCompile := true

inThisBuild(
  List(
    developers := List(
      Developer("sgeorgakis", "Stefanos Georgakis", "s_georgakis@hotmail.com", url("https://github.com/sgeorgakis"))
    )
  )
)

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
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(scalaVersion := ProjectScalaVersion)
  .dependsOn(service)
  .settings(libraryDependencies ++= Dependencies.logging)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.guice,
      Dependencies.akkaActor,
      Dependencies.akkaStream,
      Dependencies.akkaHttp,
      Dependencies.akkaSpray,
      Dependencies.akkaStreamsTestKit,
      Dependencies.akkaHttpTestKit,
      Dependencies.mockito
    )
  )
  .settings(libraryDependencies += Dependencies.scalaTest)
  .settings(Compile / mainClass := Some("com.gwi.server.MainApp"))
  .settings(
    Docker / packageName := "gwi/scala-challenge",
    packageDescription := "The Scala challenge implementation by sgeorgakis",
    dockerBaseImage := "adoptopenjdk:11-jre-hotspot", // arm compatible base image
    dockerUpdateLatest := true,
    dockerExposedPorts ++= Seq(8080)
  )
