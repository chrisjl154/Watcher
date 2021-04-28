package stream
import anomaly.{AnomalyDetectionEngine, AnomalyMessage}
import domain.{MetricTarget, MetricTargetAndResultWrapper}
import cats.effect.{ContextShift, Async, Timer, ExitCode, IO, Sync}
import cats.syntax._
import cats.implicits._
import config.{ApplicationMetricProcessingConfig, Config, KafkaConfig}
import fs2.Stream
import fs2.kafka.{ProducerRecord, Serializer, ProducerResult, ProducerSettings, ProducerRecords, KafkaProducer}
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

  /**
   * Runs the steeam forever
   * @param watchList the list of targets to watch
   * @return
   */
  override def runForever(watchList: Seq[MetricTarget]): IO[ExitCode] =
    prometheusStream(watchList).compile.drain.as(ExitCode.Success)

  /**
   * Defines the stream to run with parallelism
   * @param watchList the list of targets to watch
   * @return
   */
  private[stream] def prometheusStream(
      watchList: Seq[MetricTarget]
  ): Stream[IO, ProducerResult[String, String, Unit]] =
    KafkaProducer.stream(producerSettings).flatMap { producer =>
      (Stream
        .awakeDelay[IO](streamConfig.streamSleepTime.seconds) >> //Ensure the stream sleeps for n seconds between runs
        Stream
          .emits(watchList)
          .covary[IO]
          .parEvalMapUnordered(streamConfig.streamParallelismMax)(retrieveMetrics)
          .parEvalMapUnordered(streamConfig.streamParallelismMax)(detectAnomalies)
          .unNone //Remove None's as they do not need to have a Kafka message generated
          .map(generateKafkaMessage)
          .parEvalMapUnordered(streamConfig.streamParallelismMax) { record =>
            producer.produce(record).flatten
          }).repeat
    }

  /**
   * Retrieves metrics using the provided query string in a case class from the metric store
   * @param target The MetricTarget case class representing a target
   * @return
   */
  private[stream] def retrieveMetrics(
      target: MetricTarget
  ): IO[Option[MetricTargetAndResultWrapper]] = {
    metricClient
      .getMetricValue(target)
      .map { result =>
        val metricResult =
          result.data.result.head.value.tail.head.toFloat
        MetricTargetAndResultWrapper(target, metricResult)
      }
      .leftMap { err =>
        log.warn(s"Error retrieving metrics for \'${target.name}\': $err")
        err
      }
      .toOption //Converts an Either to an option
      .value //Extracts a value
  }

  /**
   * Uses the anomaly detection engine to determine if an anomaly has occured
   * @param resOption
   * @return
   */
  private[stream] def detectAnomalies(
      resOption: Option[MetricTargetAndResultWrapper]
  ): IO[Option[AnomalyMessage]] =
    IO {
      resOption.flatMap { res =>
        anomalyDetectionEngine.detect(res.target, res.result)
      }
    }

  /**
   * Generates a Kafka message from an anomaly. Will contain data needed by upstream components as well as some metadata
   * @param message
   * @return
   */
  private[stream] def generateKafkaMessage(
      message: AnomalyMessage
  ): ProducerRecords[String, String, Unit] =
    ProducerRecords.one[String, String](
      ProducerRecord(
        kafkaConfig.anomalyTopic,
        UUID.randomUUID().toString, //Create a new UUID for this event
        message.asJson.toString
      )
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
