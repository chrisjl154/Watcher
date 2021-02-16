package stream
import anomaly.{AnomalyDetectionEngine, AnomalyMessage}
import domain.{MetricTarget, MetricTargetAndResultWrapper}
import cats.effect.{ContextShift, Async, Timer, IO, Sync}
import cats.syntax._
import cats.implicits._
import config.{ApplicationMetricProcessingConfig, Config}
import fs2.Stream
import org.slf4j.{Logger, LoggerFactory}
import web.PrometheusMetricClient

import scala.concurrent.duration._

class PrometheusMetricWatchStream(
    streamConfig: ApplicationMetricProcessingConfig,
    metricClient: PrometheusMetricClient,
    anomalyDetectionEngine: AnomalyDetectionEngine
)(implicit
    cs: ContextShift[IO],
    timer: Timer[IO],
    sync: Sync[IO],
    async: Async[IO]
) extends MetricWatchStream {
  val log: Logger =
    LoggerFactory.getLogger(getClass.getSimpleName)

  override def runForever(watchList: Seq[MetricTarget]): IO[Unit] =
    prometheusStream(watchList).repeat
      .metered(streamConfig.streamSleepTime.seconds)
      .compile
      .drain

  private[stream] def prometheusStream(
      watchList: Seq[MetricTarget]
  ): Stream[IO, Option[String]] =
    Stream
      .emits(watchList)
      .covary[IO]
      .parEvalMapUnordered(streamConfig.streamParallelismMax)(retrieveMetrics)
      .parEvalMapUnordered(streamConfig.streamParallelismMax)(detectAnomalies)
      .filter(_.isDefined)
      .map(_.get)
      .parEvalMapUnordered(streamConfig.streamParallelismMax)(notifyKafka)

  private[stream] def retrieveMetrics(
      target: MetricTarget
  ): IO[Option[MetricTargetAndResultWrapper]] = {
    metricClient
      .getMetricValue(target)
      .map { result =>
        val metricResult =
          result.data.result.head.value.tail.head.toFloat //TODO: This is not safe. Need to find a workaround
        MetricTargetAndResultWrapper(target, metricResult)
      }
      .leftMap { err =>
        log.warn(s"Error retrieving metrics for \'${target.name}\': $err")
        err
      }
      .toOption
      .value
  }
  private[stream] def detectAnomalies(
      resOption: Option[MetricTargetAndResultWrapper]
  ): IO[Option[AnomalyMessage]] =
    IO {
      resOption.flatMap { res =>
        anomalyDetectionEngine.detect(res.target, res.result)
      }
    }

  private[stream] def notifyKafka(
      message: AnomalyMessage
  ): IO[Option[String]] =
    IO(Option("NYI"))
}

object PrometheusMetricWatchStream {
  def apply(
      config: Config,
      metricClient: PrometheusMetricClient,
      anomalyDetectionEngine: AnomalyDetectionEngine
  )(implicit
      cs: ContextShift[IO],
      timer: Timer[IO],
      sync: Sync[IO],
      async: Async[IO]
  ): PrometheusMetricWatchStream =
    new PrometheusMetricWatchStream(
      config.applicationMetricProcessingConfig,
      metricClient,
      anomalyDetectionEngine
    )
}
