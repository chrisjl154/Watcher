package application

import config.Config
import cats.effect.{Timer, IO, ExitCode, ContextShift}
import domain.MetricTarget
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import stream.PrometheusMetricWatchStream

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class Application()(implicit
    ec: ExecutionContext,
    cs: ContextShift[IO],
    timer: Timer[IO]
) {
  //TODO: Remove, this is here simply for testing purposes until a proper test env is created
  private val targets = Seq[MetricTarget](
    MetricTarget(
      "QueryOne",
      "istio_request_bytes_count{app=\"adservice\",grpc_response_status=\"0\"}",
      "10"
    ),
  )

  def execute(): IO[ExitCode] = {
    val config = loadConfig

    withBlazeClient(
      httpExecutionContext(config.httpConfig.maxConcurrentRequests)
    ) { client =>
      for {
        res <- PrometheusMetricWatchStream(
          config,
          client
        ).runForever(targets)
          .map(_ => ExitCode.Success) //TODO: Properly handle fatal errors
      } yield res
    }
  }

  private def httpExecutionContext(
      maxConcurrentRequests: Int
  ): ExecutionContextExecutor =
    ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(maxConcurrentRequests)
    )

  private def withBlazeClient(
      executionContext: ExecutionContext
  )(f: Client[IO] => IO[ExitCode]): IO[ExitCode] =
    BlazeClientBuilder[IO](executionContext).resource.use(f(_))

  private def loadConfig: Config =
    ConfigSource.default.loadOrThrow[Config]
}
