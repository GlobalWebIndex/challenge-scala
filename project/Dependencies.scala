import sbt._

object Dependencies {

  object version {

    val GuiceVersion = "5.1.0"

    val ScalaTestVersion = "3.2.14"
    val MockitoVersion = "1.17.12"

    val ScalaLoggingVersion = "3.9.5"
    val LogbackVersion = "1.4.4"
    val Slf4jVersion = "2.0.3"

    val AkkaVersion = "2.7.0"
    val AkkaHttpVersion = "10.4.0"
    val AlpakkaVersion = "5.0.0"

    val AkkaSlickVersion = "5.0.0"
    val SlickVersion = "3.4.1"
    val SlickMigrationVersion = "0.9.0"
    val PostgresVersion = "42.5.0"

  }

  lazy val guice = "com.google.inject" % "guice" % version.GuiceVersion

  lazy val scalaTest = "org.scalatest" %% "scalatest" % version.ScalaTestVersion % Test
  lazy val mockito = "org.mockito" %% "mockito-scala-scalatest" % version.MockitoVersion % Test

  lazy val logging = Seq(
    "ch.qos.logback" % "logback-classic" % version.LogbackVersion,
    "org.slf4j" % "log4j-over-slf4j" % version.Slf4jVersion,
    "com.typesafe.scala-logging" %% "scala-logging" % version.ScalaLoggingVersion
  )
  lazy val akkaActor = "com.typesafe.akka" %% "akka-actor-typed" % version.AkkaVersion
  lazy val akkaLogging = "com.typesafe.akka" %% "akka-slf4j" % version.AkkaVersion
  lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % version.AkkaVersion
  lazy val akkaHttp = "com.typesafe.akka" %% "akka-http" % version.AkkaHttpVersion
  lazy val akkaSpray = "com.typesafe.akka" %% "akka-http-spray-json" % version.AkkaHttpVersion
  lazy val alpakkaCsv = "com.lightbend.akka" %% "akka-stream-alpakka-csv" % version.AlpakkaVersion
  lazy val akkaStreamsTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % version.AkkaVersion % Test

  lazy val slick = Seq(
    "org.postgresql" % "postgresql" % version.PostgresVersion,
    "com.typesafe.slick" %% "slick" % version.SlickVersion,
    "com.lightbend.akka" %% "akka-stream-alpakka-slick" % version.AkkaSlickVersion,
    "org.slf4j" % "slf4j-nop" % version.Slf4jVersion,
    "com.typesafe.slick" %% "slick-hikaricp" % version.SlickVersion,
    "io.github.nafg.slick-migration-api" %% "slick-migration-api" % version.SlickMigrationVersion
  )
}
