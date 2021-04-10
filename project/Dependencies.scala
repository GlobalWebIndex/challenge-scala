import sbt._

object Dependencies {
  private val akkaActor       = "com.typesafe.akka"           %% "akka-actor-typed"                     % VersionsOf.akka
  private val akkaStream      = "com.typesafe.akka"           %% "akka-stream"                          % VersionsOf.akka
  private val akkaHttp        = "com.typesafe.akka"           %% "akka-http"                            % VersionsOf.akkaHttp
  private val akkaHttpSpray   = "com.typesafe.akka"           %% "akka-http-spray-json"                 % VersionsOf.akkaHttp
  private val akkaHttpTest    = "com.typesafe.akka"           %% "akka-http-testkit"                    % VersionsOf.akkaHttp       % Test
  private val akkaStreamTest  = "com.typesafe.akka"           %% "akka-stream-testkit"                  % VersionsOf.akka           % Test
  private val alpakkaCsv      = "com.lightbend.akka"          %% "akka-stream-alpakka-json-streaming"   % VersionsOf.alpakka
  private val alpakkaJson     = "com.lightbend.akka"          %% "akka-stream-alpakka-csv"              % VersionsOf.alpakka
  private val betterFiles     = "com.github.pathikrit"        %% "better-files"                         % VersionsOf.betterFiles
  private val fs2Core         = "co.fs2"                      %% "fs2-core"                             % VersionsOf.fs2
  private val fs2IO           = "co.fs2"                      %% "fs2-io"                              % VersionsOf.fs2
  private val fs2DataCsv      = "org.gnieh"                   %% "fs2-data-csv"                         % VersionsOf.fs2Data
  private val fs2DataCsvGen   = "org.gnieh"                   %% "fs2-data-csv-generic"                 % VersionsOf.fs2Data
  private val fs2DataJson     = "org.gnieh"                   %% "fs2-data-json"                        % VersionsOf.fs2Data
  private val janino          = "org.codehaus.janino"         %  "janino"                               % VersionsOf.janino
  private val logbackClassic  = "ch.qos.logback"              %  "logback-classic"                      % VersionsOf.logbackClassic
  private val metricsScala    = "nl.grons"                    %% "metrics4-scala"                       % VersionsOf.metricsScala
  private val scalaLogging    = "com.typesafe.scala-logging"  %% "scala-logging"                        % VersionsOf.scalaLogging
  private val scalatest       = "org.scalatest"               %% "scalatest"                            % VersionsOf.scalatest      % Test

  val all: Seq[ModuleID] = Seq(
    akkaActor,
    akkaStream,
    akkaHttp,
    akkaHttpSpray,
    akkaHttpTest,
    akkaStreamTest,
    alpakkaCsv,
    alpakkaJson,
    betterFiles,
    fs2Core,
    fs2IO,
    fs2DataCsv,
    fs2DataCsvGen,
    fs2DataJson,
    janino,
    logbackClassic,
    metricsScala,
    scalaLogging,
    scalatest
  )
}