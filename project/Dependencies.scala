import sbt._

object Dependencies {
  val akkaVersion = "2.6.20"
  val akkaHttpVersion = "10.2.10"
  val logbackVersion = "1.2.3"
  val scalaTestVersion = "3.1.0"
  val circeVersion = "0.14.1"

  val circeDependencies: Seq[ModuleID] = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser"
  ).map(_ % circeVersion)

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaActorTyped = "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
  val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % akkaVersion
  val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val akkaDependencies: Seq[ModuleID] = Seq(akkaActor, akkaActorTyped, akkaCluster, akkaHttp)

  val logback = "ch.qos.logback" % "logback-classic" % logbackVersion

  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % Test
  val akkaActorTestkit = "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test
  val testDependencies: Seq[ModuleID] = Seq(scalaTest, akkaActorTestkit)

  val dependencies: Seq[ModuleID] = logback +: akkaDependencies ++: testDependencies ++: circeDependencies
}
