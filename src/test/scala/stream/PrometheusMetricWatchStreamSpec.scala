package stream

import anomaly.AnomalyMessage
import cats.Parallel
import cats.effect.{IO, ContextShift}
import domain.{MetricTarget, PrometheusQueryResult}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.concurrent.Eventually
import test.TestSupport._

import scala.concurrent.ExecutionContext

class PrometheusMetricWatchStreamSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with BeforeAndAfterAll {
  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  private implicit val parallel: Parallel[IO] = IO.ioParallel

  "PrometheusMetricWatchStream.validate" should {
    s"Produce no AnomalyMessages" when {
      "A valid set of targets are provided that do not violate thresholds" in {
        val targets: Seq[MetricTarget] = loadValidTargetsNoAnomaly

        withConfig() { config =>
          withAnomalyDetectionEngine() { engine =>
            withHardcodedPrometheusMetricClient() { metricClient =>
              withPrometheusMetricWatchStream(config, metricClient, engine) {
                stream =>
                  val prometheusStream =
                    stream.prometheusStream(targets).compile
                  val result = prometheusStream.toList.unsafeRunSync()

                  result.length mustEqual (0)
              }
            }
          }
        }
      }
    }
    s"Produce AnomalyMessages" when {
      "A valid set of targets are provided along with invalid targets" in {
        val validTargets: Seq[MetricTarget] = loadValidTargetsWithAnomaly
        val invalidTargets: Seq[MetricTarget] = loadInvalidTargets

        withConfig() { config =>
          withAnomalyDetectionEngine() { engine =>
            withHardcodedPrometheusMetricClient() { metricClient =>
              withPrometheusMetricWatchStream(config, metricClient, engine) {
                stream =>
                  val prometheusStream =
                    stream
                      .prometheusStream(validTargets ++ invalidTargets)
                      .compile
                  val result = prometheusStream.toList.unsafeRunSync()

                  result.size mustEqual 1
              }
            }
          }
        }
      }
    }
  }
}
