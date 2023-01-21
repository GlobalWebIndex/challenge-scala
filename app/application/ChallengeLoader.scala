package application

import com.softwaremill.macwire._
import controllers.Assets
import controllers.CheckController
import controllers.CsvToJsonController
import conversion.ConversionConfig
import conversion.FileSaver
import conversion.HttpConversion
import conversion.UUIDNamer
import pool.DefaultWorkerFactory
import pool.WorkerPool
import router.Routes

import play.api.Application
import play.api.ApplicationLoader
import play.api.BuiltInComponentsFromContext
import play.api.LoggerConfigurator
import play.api.routing.Router
import play.filters.HttpFiltersComponents

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

  private lazy val config = configuration.underlying
  private lazy val conversionConfig: ConversionConfig =
    ConversionConfig.fromConf(config.getConfig("conversion"))

  private lazy val workerFactory =
    new DefaultWorkerFactory(HttpConversion, FileSaver)

  private lazy val conversionPool =
    new WorkerPool(conversionConfig, workerFactory, FileSaver, UUIDNamer)

  private lazy val checkController = new CheckController(controllerComponents)
  private lazy val csvToJsonController =
    new CsvToJsonController(config, controllerComponents, conversionPool)

  override def router: Router = {
    lazy val prefix: String = ""
    lazy val assetsController = wire[Assets]
    wire[Routes]
  }
}
