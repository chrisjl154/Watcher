package stream

import cats.Parallel
import cats.effect.{IO, ContextShift}
import domain.MetricTarget
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

  "PrometheusMetricWatchStream" should {
    "Successfully run through the stream once" when {
      "A valid set of targets are provided" in {
        val targets: Seq[MetricTarget] = loadValidTargets

        withConfig() { config =>
          withHardcodedPrometheusMetricClient() { metricClient =>
            withPrometheusMetricWatchStream(config, metricClient) { stream =>
              val prometheusStream = stream.prometheusStream(targets).compile
              val result = prometheusStream.toList.unsafeRunSync()

              result mustNot contain(None)
            }
          }
        }
      }
      "A valid set of targets are provided along with invalid targets" in {
        val validTargets: Seq[MetricTarget] = loadValidTargets
        val invalidTargets: Seq[MetricTarget] = loadInvalidTargets

        withConfig() { config =>
          withHardcodedPrometheusMetricClient() { metricClient =>
            withPrometheusMetricWatchStream(config, metricClient) { stream =>
              val prometheusStream = stream.prometheusStream(validTargets ++ invalidTargets).compile
              val result = prometheusStream.toList.unsafeRunSync()

              val succeeded = result.filter(_.isDefined)
              val failed = result.filterNot(_.isDefined)

              succeeded.size mustEqual 5
              failed.size mustEqual 4
            }
          }
        }
      }
    }
  }
}
