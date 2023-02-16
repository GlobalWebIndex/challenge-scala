import sbt._

object Dependencies {
  val akkaVersion = "2.6.20"
  val akkaHttpVersion = "10.2.10"

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaActorTyped = "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
  val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % akkaVersion
  val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion

  val akkaDependencies: Seq[ModuleID] = Seq(akkaActor, akkaActorTyped, akkaCluster, akkaHttp)
}
