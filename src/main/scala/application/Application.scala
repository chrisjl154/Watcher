package application

import config.Config
import cats.effect.{Timer, IO, ExitCode, ContextShift}
import domain.Target
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import pureconfig.ConfigSource
import pureconfig.generic.auto._ //required
import stream.HttpApplicationMetricWatchStream

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

class Application()(implicit
    ec: ExecutionContext,
    cs: ContextShift[IO],
    timer: Timer[IO]
) {
  private val targets = Seq[Target](
    Target("Chris"),
    Target("Nikita"),
    Target("Lara"),
    Target("Alex"),
    Target("Elle")
  )

  def execute(): IO[ExitCode] = {
    val config = loadConfig
    val httpExecutionContext = ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(config.httpConfig.maxConcurrentRequests)
    )

    withBlazeClient(httpExecutionContext) { client =>
      for {
        res <- HttpApplicationMetricWatchStream(
          config,
          client
        ).runForever(targets)
          .map(_ => ExitCode.Success) //TODO: Properly handle fatal errors
      } yield res
    }
  }

  private def withBlazeClient(
      executionContext: ExecutionContext
  )(f: Client[IO] => IO[ExitCode]): IO[ExitCode] =
    BlazeClientBuilder[IO](executionContext).resource.use(f(_))

  private def loadConfig: Config =
    ConfigSource.default.loadOrThrow[Config]
}
