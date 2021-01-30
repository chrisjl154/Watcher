package application

import config.Config
import cats.effect.{ContextShift,ExitCode, IO}
import org.slf4j.{LoggerFactory, Logger}
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import scala.concurrent.ExecutionContext

class Application()(implicit
    ec: ExecutionContext,
    cs: ContextShift[IO]
) {
  private val logger: Logger = LoggerFactory.getLogger("Application")

  def execute(): IO[ExitCode] =
    IO {
      logger.info("Starting application")
      val conf = loadConfig
      logger.debug(s"Got test config with value: ${conf.example}")
      ExitCode.Success
    }

  private def loadConfig: Config =
    ConfigSource.default.loadOrThrow[Config]
}
