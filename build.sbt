ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val root = (project in file(".")).settings(
    name := "challenge-scala",
    idePackagePrefix := Some("com.gwi.karelsk")
  )

scalacOptions := Seq("-unchecked", "-deprecation")

val ConfigVersion = "1.4.2"
val AkkaVersion = "2.6.19"
val AkkaHttpVersion = "10.2.9"
libraryDependencies ++= Seq(
  "com.typesafe" % "config" % ConfigVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "org.scalatest" %% "scalatest" % "3.2.11" % Test,
  "ch.qos.logback" % "logback-classic" % "1.2.11",
  "com.opencsv" % "opencsv" % "5.6"
)
