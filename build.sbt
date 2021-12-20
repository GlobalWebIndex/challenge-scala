lazy val commonSettings = Seq(
  name := "csv-to-json",
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.12.8",
  scalacOptions ++= Seq(
    "-deprecation",
    "-Xfatal-warnings",
    "-Ywarn-value-discard",
    "-Xlint:missing-interpolator"
  )
)

lazy val SttpVersion = "1.7.2"
lazy val Http4sVersion = "0.21.22"
lazy val PureConfigVersion = "0.12.3"
lazy val TypesafeConfigVersion = "1.4.0"
lazy val Slf4jVersion = "1.0.1"
lazy val LogbackVersion = "1.2.3"
lazy val ScalaMockVersion = "4.4.0"
lazy val CirceVersion = "0.13.0"


lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    Defaults.itSettings,
    libraryDependencies ++= Seq(
      "org.scalactic" %% "scalactic" % "3.2.0",
      "org.scalatest" %% "scalatest" % "3.2.0" % Test,
      "com.softwaremill.sttp" %% "core" % SttpVersion,
      "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s" %% "http4s-circe" % Http4sVersion,
      "org.http4s" %% "http4s-dsl" % Http4sVersion,
      "com.typesafe" % "config" % TypesafeConfigVersion,
      "io.chrisdavenport" %% "log4cats-slf4j" % Slf4jVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "org.scalamock" %% "scalamock" % ScalaMockVersion % Test,
      "io.circe" %% "circe-generic" % CirceVersion,
      "io.circe" %% "circe-core" % CirceVersion,
      "io.circe" %% "circe-generic" % CirceVersion,
      "io.circe" %% "circe-parser" % CirceVersion,
      "io.circe" %% "circe-literal" % CirceVersion % "it,test",
      "io.circe" %% "circe-optics" % CirceVersion % "it"
    )
  )