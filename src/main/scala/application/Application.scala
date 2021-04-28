package application

import anomaly.AnomalyDetectionEngine
import config.Config
import cats.syntax._
import cats.implicits._
import cats.effect.{ContextShift, Resource, Timer, ExitCode, IO}
import domain.MetricTargetValidator
import fs2.kafka.{KafkaProducer, ProducerSettings, ProducerResource}
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

  /**
   * Load targets and service definitions and Execute the application
   * @return
   */
  def execute(): IO[ExitCode] = {
    val config = loadConfig
    withBlazeClient(
      httpExecutionContext(config.httpConfig.maxConcurrentRequests)
    ) { client =>
      for {
        targets <-
          HardcodedTargetLoader.loadAll(config.targetDefinitions.source)
        validatedTargets = MetricTargetValidator.validateAll(targets)
        _ = log.info(
          s"Validated ${validatedTargets.length}/${targets.length} supplied targets"
        )
        res <- PrometheusMetricWatchStream(
          config,
          HttpPrometheusPrometheusMetricClient(config.prometheusConfig, client),
          AnomalyDetectionEngine()
        ).runForever(validatedTargets)
      } yield res
    }
  }

  /**
   * Creates a thread pool for HTTP requests
   * @param maxConcurrentRequests
   * @return
   */
  private def httpExecutionContext(
      maxConcurrentRequests: Int
  ): ExecutionContextExecutor =
    ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(maxConcurrentRequests)
    )

  /**
   * Execute a given function in the context of a Blaze client
   * @param executionContext A thread pool for HTTP requests
   * @param f A function to execute
   * @return
   */
  private def withBlazeClient(
      executionContext: ExecutionContext
  )(f: Client[IO] => IO[ExitCode]): IO[ExitCode] =
    BlazeClientBuilder[IO](executionContext).resource.use(f(_))

  private def loadConfig: Config =
    ConfigSource.default.loadOrThrow[Config]
}
