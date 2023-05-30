val http4sVersion          = "1.0.0-M39"
val catsEffectVersion      = "3.5.0"
val catsCoreVersion        = "2.9.0"
val logbackVersion         = "1.4.6"
val circeVersion           = "0.14.5"
val MunitVersion           = "0.7.29"
val MunitCatsEffectVersion = "1.0.7"

ThisBuild / scalaVersion := "2.13.2"

libraryDependencies ++= Seq(
  "org.http4s"           %% "http4s-ember-server" % http4sVersion,
  "org.http4s"           %% "http4s-ember-client" % http4sVersion,
  "org.http4s"           %% "http4s-dsl"          % http4sVersion,
  "org.http4s"           %% "http4s-circe"        % http4sVersion,
  "org.typelevel"        %% "cats-effect"         % catsEffectVersion,
  "org.typelevel"        %% "cats-core"           % catsCoreVersion,
  "ch.qos.logback"        % "logback-classic"     % logbackVersion,
  "io.circe"             %% "circe-generic"       % circeVersion,
  "org.gnieh"            %% "fs2-data-csv"        % "1.7.1",
  "com.github.tototoshi" %% "scala-csv"           % "1.3.10",
  "org.scalameta"        %% "munit"               % MunitVersion           % Test,
  "org.typelevel"        %% "munit-cats-effect-3" % MunitCatsEffectVersion % Test
)

testFrameworks += new TestFramework("munit.Framework")
