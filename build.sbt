lazy val akkaHttpVersion = "10.4.0"
lazy val akkaVersion     = "2.7.0"
lazy val logbackVersion  = "1.4.4"
lazy val pgVersion       = "42.5.0"
lazy val slickVersion    = "3.4.1"

fork := true

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.github",
      scalaVersion    := "2.13.4"
    )),
    name := "csv-importer",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-http"                 % akkaHttpVersion,
      "com.typesafe.akka"  %% "akka-http-spray-json"      % akkaHttpVersion,
      "com.typesafe.akka"  %% "akka-actor-typed"          % akkaVersion,
      "com.typesafe.akka"  %% "akka-stream"               % akkaVersion,
      "ch.qos.logback"     % "logback-classic"            % logbackVersion,
      "org.postgresql"     % "postgresql"                 % pgVersion,
      "com.typesafe.slick" %% "slick"                     % slickVersion,
      "com.typesafe.slick" %% "slick-hikaricp"            % slickVersion,

      "com.typesafe.akka" %% "akka-http-testkit"          % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed"   % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"                  % "3.2.9"         % Test
    )
  )
