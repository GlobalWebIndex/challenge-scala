package controllers

import application.ChallengeStartup

import play.api.{Application, ApplicationLoader, Environment, Mode}

object Mock {
  lazy val context: ApplicationLoader.Context =
    ApplicationLoader.Context.create(
      environment = new Environment(
        rootPath = new java.io.File("."),
        classLoader = ApplicationLoader.getClass.getClassLoader,
        mode = Mode.Test
      )
    )
  lazy val application: Application = new ChallengeStartup(context).application
}
