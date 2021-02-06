package stream
import domain.{MetricTarget, PrometheusQueryResult}
import cats.effect.{ContextShift, Async, Timer, IO, Sync}
import cats.syntax._
import config.{ApplicationMetricProcessingConfig, Config}
import fs2.Stream
import org.slf4j.{Logger, LoggerFactory}
import web.{HttpPrometheusPrometheusMetricClient, PrometheusMetricClient}

import scala.concurrent.duration._

class PrometheusMetricWatchStream(
    streamConfig: ApplicationMetricProcessingConfig,
    metricClient: PrometheusMetricClient
)(implicit
    cs: ContextShift[IO],
    timer: Timer[IO],
    sync: Sync[IO],
    async: Async[IO]
) extends MetricWatchStream {
  val log: Logger =
    LoggerFactory.getLogger(PrometheusMetricWatchStream.getClass.getName)

  override def runForever(watchList: Seq[MetricTarget]): IO[Unit] =
    Stream
      .emits(watchList)
      .covary[IO]
      .parEvalMapUnordered(streamConfig.streamParallelismMax)(process)
      .parEvalMapUnordered(streamConfig.streamParallelismMax)(validate)
      .repeat
      .metered(streamConfig.streamSleepTime.seconds)
      .compile
      .drain

  private def process(
      query: MetricTarget
  ): IO[Either[String, PrometheusQueryResult]] = {
    log.info(s"Processing query ${query} in stream")
    metricClient.getMetricValue(query)
  }

  private def validate(
      res: Either[String, PrometheusQueryResult]
  ): IO[Option[PrometheusQueryResult]] = {
    log.info(s"Validating query response ${res}")
    IO.pure(res.toOption)
  }
}

object PrometheusMetricWatchStream {
  def apply(config: Config, metricClient: HttpPrometheusPrometheusMetricClient)(
      implicit
      cs: ContextShift[IO],
      timer: Timer[IO],
      sync: Sync[IO],
      async: Async[IO]
  ): PrometheusMetricWatchStream =
    new PrometheusMetricWatchStream(
      config.applicationMetricProcessingConfig,
      metricClient
    )
}
