package application

import com.softwaremill.macwire._
import controllers.Assets
import controllers.CheckController
import controllers.CsvToJsonController
import conversion.ConversionConfig
import conversion.ConversionService
import conversion.DefaultConversionWorkerCreator
import conversion.FileConversionSink
import conversion.HttpConversionSource
import conversion.UUIDNamer
import play.api.Application
import play.api.ApplicationLoader
import play.api.BuiltInComponentsFromContext
import play.api.LoggerConfigurator
import play.api.routing.Router
import play.filters.HttpFiltersComponents
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

  private lazy val conversionConfig = wireWith(ConversionConfig.fromConf _)

  private lazy val conversionSource = HttpConversionSource
  private lazy val conversionSink = FileConversionSink
  private lazy val namer = UUIDNamer

  private lazy val workerCreator = wire[DefaultConversionWorkerCreator]

  private lazy val conversionService = wire[ConversionService]

  private lazy val assetsController = wire[Assets]

  private lazy val checkController = wire[CheckController]
  private lazy val csvToJsonController = wire[CsvToJsonController]

  override def router: Router = {
    lazy val prefix: String = ""
    wire[Routes]
  }
}
