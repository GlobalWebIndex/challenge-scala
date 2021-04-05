lazy val `challenge-scala` = (project in file("."))
  .settings(
    organization := "gwi",
    scalaVersion := "2.13.5",
    libraryDependencies ++= Dependencies.deps,
    parallelExecution in Test := false
  )
