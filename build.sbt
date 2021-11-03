lazy val AkkaVersion    = "2.6.17"
lazy val AkkaHttpVersion = "10.2.6"
lazy val AkkaHttpJsonSerializersVersion = "1.38.2"
lazy val AlpakkaVersion = "3.0.3"
lazy val CirceVersion = "0.14.1"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.example",
      scalaVersion    := "2.13.4"
    )),
    name := "akka-http-quickstart-scala",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"                % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json"     % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-actor-typed"         % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream"              % AkkaVersion,

      // Alpakka
      "com.lightbend.akka" %% "akka-stream-alpakka-csv" % AlpakkaVersion,


      // Circe
      "io.circe" %% "circe-core" % CirceVersion,
      "io.circe" %% "circe-generic" % CirceVersion,
      "io.circe" %% "circe-parser" % CirceVersion,
      "io.circe" %% "circe-generic-extras" % CirceVersion,

      "de.heikoseeberger" %% "akka-http-circe" % AkkaHttpJsonSerializersVersion,


      "ch.qos.logback"    % "logback-classic"           % "1.2.3",
      "com.typesafe.akka" %% "akka-http-testkit"        % AkkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion     % Test,
      "org.scalatest"     %% "scalatest"                % "3.1.4"         % Test
    )
  )
