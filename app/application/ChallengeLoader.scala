package application

import com.softwaremill.macwire._
import controllers.Assets
import controllers.CheckController
import controllers.CsvToJsonController
import conversion.FileSaver
import conversion.HttpConversion
import conversion.UUIDNamer
import play.api.Application
import play.api.ApplicationLoader
import play.api.BuiltInComponentsFromContext
import play.api.LoggerConfigurator
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import pool.Config
import pool.DefaultWorkerFactory
import pool.WorkerPool
import router.Routes

class ChallengeLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }
    new ChallengeStartup(context).application
  }
}

class ChallengeStartup(context: ApplicationLoader.Context)
    extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with controllers.AssetsComponents {

  private lazy val conversionConfig = wireWith(Config.fromConf _)

  private lazy val conversionSource = HttpConversion
  private lazy val conversionSink = FileSaver
  private lazy val namer = UUIDNamer

  private lazy val workerCreator = wire[DefaultWorkerFactory]

  private lazy val conversionService = wire[WorkerPool]

  private lazy val assetsController = wire[Assets]

  private lazy val checkController = wire[CheckController]
  private lazy val csvToJsonController = wire[CsvToJsonController]

  override def router: Router = {
    lazy val prefix: String = ""
    wire[Routes]
  }
}
