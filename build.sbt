lazy val akkaHttpVersion = "10.2.10"
lazy val akkaVersion     = "2.6.20"

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
fork := true

lazy val root = (project in file(".")).settings(
  inThisBuild(
    List(
      organization := "com.gwi",
      scalaVersion := "2.13.4"
    )
  ),
  name := "gwi",
  libraryDependencies ++= Seq(
    "com.typesafe.akka"    %% "akka-http"                % akkaHttpVersion,
    "com.typesafe.akka"    %% "akka-http-spray-json"     % akkaHttpVersion,
    "com.typesafe.akka"    %% "akka-actor-typed"         % akkaVersion,
    "com.typesafe.akka"    %% "akka-stream-typed"        % akkaVersion,
    "ch.qos.logback"        % "logback-classic"          % "1.2.3",
    "com.github.tototoshi" %% "scala-csv"                % "1.3.10",
    "org.scalatest"        %% "scalatest"                % "3.2.4",
    "org.scalatest"        %% "scalatest"                % "3.2.4"         % Test,
    "com.typesafe.akka"    %% "akka-http-testkit"        % akkaHttpVersion % Test,
    "com.typesafe.akka"    %% "akka-actor-testkit-typed" % akkaVersion     % Test
  )
)
