package stream
import anomaly.{AnomalyDetectionEngine, AnomalyMessage}
import domain.{MetricTarget, MetricTargetAndResultWrapper}
import cats.effect.{ContextShift, Async, Timer, ExitCode, IO, Sync}
import cats.syntax._
import cats.implicits._
import config.{ApplicationMetricProcessingConfig, Config, KafkaConfig}
import fs2.Stream
import fs2.kafka.{ProducerRecord, Serializer, ProducerSettings, ProducerRecords, KafkaProducer}
import org.slf4j.{Logger, LoggerFactory}
import web.PrometheusMetricClient
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax._

import java.util.UUID
import scala.concurrent.duration._

class PrometheusMetricWatchStream(
    streamConfig: ApplicationMetricProcessingConfig,
    kafkaConfig: KafkaConfig,
    metricClient: PrometheusMetricClient,
    anomalyDetectionEngine: AnomalyDetectionEngine
)(implicit
    cs: ContextShift[IO],
    timer: Timer[IO],
    sync: Sync[IO],
    async: Async[IO]
) extends MetricWatchStream {
  private val log: Logger =
    LoggerFactory.getLogger(getClass.getSimpleName)

  private val producerSettings: ProducerSettings[IO, String, String] =
    ProducerSettings(
      keySerializer = Serializer[IO, String],
      valueSerializer = Serializer[IO, String]
    ).withBootstrapServers(
      kafkaConfig.bootstrapServer
    )

  override def runForever(watchList: Seq[MetricTarget]): IO[ExitCode] =
    prometheusStream(watchList).compile.drain.as(ExitCode.Success)

  private[stream] def prometheusStream(
      watchList: Seq[MetricTarget]
  ) =
    KafkaProducer.stream(producerSettings).flatMap { producer =>
      Stream
        .emits(watchList)
        .covary[IO]
        .parEvalMapUnordered(streamConfig.streamParallelismMax)(retrieveMetrics)
        .parEvalMapUnordered(streamConfig.streamParallelismMax)(detectAnomalies)
        .filter(_.isDefined)
        .map(_.get)
        .map(notifyKafka)
        .evalMap { record =>
          producer.produce(record).flatten
        }
        .repeat
        .metered(streamConfig.streamSleepTime.seconds)
    }

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
  ): ProducerRecords[String, String, Unit] =
    ProducerRecords.one[String, String](
      ProducerRecord(kafkaConfig.anomalyTopic, UUID.randomUUID().toString, message.asJson.toString)
    )
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
      config.kafkaConfig,
      metricClient,
      anomalyDetectionEngine
    )
}
