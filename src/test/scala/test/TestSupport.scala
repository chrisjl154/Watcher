package test

import scala.concurrent.ExecutionContext
import config.{
  ApplicationMetricProcessingConfig,
  Config,
  HttpConfig,
  PrometheusConfig
}
import cats.Parallel
import web.{HardcodedPrometheusMetricClient, PrometheusMetricClient}
import cats.effect.{Timer, IO, ContextShift}
import domain.{MetricTarget, metricTargetDecoder, MetricTargetCandidate}
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
      metricWatchStream: PrometheusMetricClient
  )(f: PrometheusMetricWatchStream => Unit): Unit =
    f(PrometheusMetricWatchStream(config, metricWatchStream))

  def withHardcodedPrometheusMetricClient()(
      f: PrometheusMetricClient => Unit
  ): Unit =
    f(HardcodedPrometheusMetricClient())

  def withConfig()(f: Config => Unit): Unit = {
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

    f(
      Config(
        applicationMetricProcessingConfig,
        httpConfig,
        prometheusConfig
      )
    )
  }

  //TODO: Kind of dirty, should be Ok as this is just for tests but maybe clean this up?
  def loadValidTargets: Seq[MetricTarget] =
    Source
      .fromResource("metricTargetsValid.json")
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
}
