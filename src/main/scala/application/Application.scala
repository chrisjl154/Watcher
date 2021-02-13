package application

import cats.data.NonEmptyChain
import config.Config
import cats.syntax._
import cats.implicits._
import cats.effect.{Timer, IO, ExitCode, ContextShift}
import domain.MetricTargetValidator.ValidationResult
import domain.{
  MetricTargetCandidate,
  UnidentifiedQueryError,
  MetricTarget,
  MetricTargetValidationError,
  MetricTargetValidator
}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.slf4j.{Logger, LoggerFactory}
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import stream.PrometheusMetricWatchStream
import web.HttpPrometheusPrometheusMetricClient

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class Application()(implicit
    ec: ExecutionContext,
    cs: ContextShift[IO],
    timer: Timer[IO]
) {
  private val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  //TODO: Remove, this is here simply for testing purposes until a proper test env is created
  private val exTargets = Seq[MetricTargetCandidate](
    MetricTargetCandidate(
      "QueryOne",
      "istio_request_bytes_count{app=\"adservice\",grpc_response_status=\"0\"}",
      "10",
      "Example query"
    ),
    MetricTargetCandidate(
      "QueryTwenty",
      "",
      "",
      ""
    ),
    MetricTargetCandidate(
      "QueryTwo",
      "istio_request_bytes_count{app=\"adservice\",grpc_response_status=\"200\"}",
      "1000",
      "Example query2"
    )
  )

  def execute(): IO[ExitCode] = {
    val config = loadConfig

    val validatedTargets =
      MetricTargetValidator.validateAll(exTargets)

    withBlazeClient(
      httpExecutionContext(config.httpConfig.maxConcurrentRequests)
    ) { client =>
      for {
        res <- PrometheusMetricWatchStream(
          config,
          HttpPrometheusPrometheusMetricClient(config.prometheusConfig, client)
        ).runForever(validatedTargets)
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
