package application

import config.Config
import cats.effect.{Timer, IO, ExitCode, ContextShift}
import domain.Target
import org.slf4j.{Logger, LoggerFactory}
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import stream.HttpApplicationMetricWatchStream

import scala.concurrent.ExecutionContext

class Application()(implicit
    ec: ExecutionContext,
    cs: ContextShift[IO],
    timer: Timer[IO]
) {
  private val logger: Logger = LoggerFactory.getLogger("Application")

  private val targets = Seq[Target](
    Target("Chris"),
    Target("Nikita"),
    Target("Lara"),
    Target("Alex"),
    Target("Elle")
  )

  def execute(): IO[ExitCode] =
    for {
      _ <- new HttpApplicationMetricWatchStream(loadConfig.httpApplicationMetricConfig).runForever(targets)
      exit = ExitCode.Success
    } yield exit

  private def loadConfig: Config =
    ConfigSource.default.loadOrThrow[Config]
}
