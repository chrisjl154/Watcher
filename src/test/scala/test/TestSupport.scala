package test

import anomaly.AnomalyDetectionEngine

import scala.concurrent.ExecutionContext
import config.{
  TargetDefinitions,
  PrometheusConfig,
  KafkaConfig,
  Config,
  HttpConfig,
  ApplicationMetricProcessingConfig
}
import cats.Parallel
import web.{HardcodedPrometheusMetricClient, PrometheusMetricClient}
import cats.effect.{Timer, IO, ContextShift}
import domain.{MetricTarget, MetricTargetCandidate, metricTargetDecoder}
import stream.PrometheusMetricWatchStream
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax._

import scala.io.Source

object TestSupport {
  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  private implicit val parallel: Parallel[IO] = IO.ioParallel
  private implicit val timer: Timer[IO] = IO.timer(ec)

  def withPrometheusMetricWatchStream(
      config: Config,
      metricWatchStream: PrometheusMetricClient,
      anomalyDetectionEngine: AnomalyDetectionEngine
  )(f: PrometheusMetricWatchStream => Unit): Unit =
    f(
      PrometheusMetricWatchStream(
        config,
        metricWatchStream,
        anomalyDetectionEngine
      )
    )

  def withHardcodedPrometheusMetricClient()(
      f: PrometheusMetricClient => Unit
  ): Unit =
    f(HardcodedPrometheusMetricClient())

  def withAnomalyDetectionEngine()(
      f: AnomalyDetectionEngine => Unit
  ): Unit =
    f(AnomalyDetectionEngine())

  def withConfig(
      kafkaConfig: KafkaConfig =
        KafkaConfig("127.0.0.1:9092", "TestGroup", "anomalies_v1")
  )(f: Config => Unit): Unit = {
    val streamParallelismMax = 10
    val streamSleepTime = 10
    val applicationMetricProcessingConfig =
      ApplicationMetricProcessingConfig(streamParallelismMax, streamSleepTime)

    val maxConcurrentRequests = 10
    val httpConfig = HttpConfig(maxConcurrentRequests)

    val prometheusHost = "localhost"
    val prometheusPort = 30003
    val prometheusApiEndpoint = "/api/v1/query"
    val prometheusConfig =
      PrometheusConfig(prometheusHost, prometheusPort, prometheusApiEndpoint)

    val targetSource = "metricTargetCandidatesValid.json"
    val targetConfig = TargetDefinitions(targetSource)

    f(
      Config(
        applicationMetricProcessingConfig,
        httpConfig,
        prometheusConfig,
        targetConfig,
        kafkaConfig
      )
    )
  }

  //TODO: Kind of dirty, should be Ok as this is just for tests but maybe clean this up?
  def loadValidTargetsWithAnomaly: Seq[MetricTarget] =
    Source
      .fromResource("metricTargetsValidWithAnomaly.json")
      .getLines()
      .map(decode[MetricTarget](_))
      .toSeq
      .filter(_.isRight)
      .map(item => item.toOption.get)

  def loadValidTargetsNoAnomaly: Seq[MetricTarget] =
    Source
      .fromResource("metricTargetsValidNoAnomalies.json")
      .getLines()
      .map(decode[MetricTarget](_))
      .toSeq
      .filter(_.isRight)
      .map(item => item.toOption.get)

  def loadValidTargetsAllAnomaly: Seq[MetricTarget] =
    Source
      .fromResource("metricTargetsValidAllAnomaly.json")
      .getLines()
      .map(decode[MetricTarget](_))
      .toSeq
      .filter(_.isRight)
      .map(item => item.toOption.get)

  def loadValidTargetQueryNames: Seq[String] =
    Source
      .fromResource("metricTargetsNamesValid.json")
      .getLines()
      .map(decode[String](_))
      .toSeq
      .filter(_.isRight)
      .map(item => item.toOption.get)

  def loadInvalidTargets: Seq[MetricTarget] =
    Source
      .fromResource("metricTargetsInvalid.json")
      .getLines()
      .map(decode[MetricTarget](_))
      .toSeq
      .filter(_.isRight)
      .map(item => item.toOption.get)

  def loadMetricTargetCandidatesValid: Seq[MetricTargetCandidate] =
    Source
      .fromResource("metricTargetCandidatesValid.json")
      .getLines()
      .map(decode[MetricTargetCandidate](_))
      .toSeq
      .filter(_.isRight)
      .map(item => item.toOption.get)

  def loadMetricTargetCandidatesInvalid: Seq[MetricTargetCandidate] =
    Source
      .fromResource("metricTargetCandidatesInvalid.json")
      .getLines()
      .map(decode[MetricTargetCandidate](_))
      .toSeq
      .filter(_.isRight)
      .map(item => item.toOption.get)

  def loadMetricTargetCandidatesValidNonExistentInPrometheus: Seq[MetricTarget] =
    Source
      .fromResource("metricTargetsValidNonExistent.json")
      .getLines()
      .map(decode[MetricTarget](_))
      .toSeq
      .filter(_.isRight)
      .map(item => item.toOption.get)
}
