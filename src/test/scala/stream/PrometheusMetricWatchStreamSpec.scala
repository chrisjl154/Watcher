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

  "PrometheusMetricWatchStream.getMetricValue" should {
    s"retrieve metric values" when {
      "a set of existent targets are supplied" in {
        val targets: Seq[MetricTarget] = loadValidTargetsNoAnomaly

        withConfig() { config =>
          withAnomalyDetectionEngine() { engine =>
            withHardcodedPrometheusMetricClient() { metricClient =>
              val result =
                targets
                  .map(metricClient.getMetricValue)
                  .map(_.value)
                  .map(_.unsafeRunSync())

              val lefts = result.filter(_.isLeft)
              val rights = result.filter(_.isRight)

              lefts.length mustEqual (0)
              rights.length mustEqual (targets.length)
            }
          }
        }
      }
    }

    s"retrieve metric values and errors" when {
      "a combination of existent and non-existent targets are supplied" in {
        val targetsExistent: Seq[MetricTarget] = loadValidTargetsNoAnomaly
        val targetsNonExistent: Seq[MetricTarget] =
          loadMetricTargetCandidatesValidNonExistentInPrometheus
        val targets: Seq[MetricTarget] = targetsExistent ++ targetsNonExistent

        withConfig() { config =>
          withAnomalyDetectionEngine() { engine =>
            withHardcodedPrometheusMetricClient() { metricClient =>
              val result =
                targets
                  .map(metricClient.getMetricValue)
                  .map(_.value)
                  .map(_.unsafeRunSync())

              val lefts = result.filter(_.isLeft)
              val rights = result.filter(_.isRight)

              result.length mustEqual (targets.length)
              lefts.length mustEqual (targetsNonExistent.length)
              rights.length mustEqual (targetsExistent.length)
            }
          }
        }
      }
    }

    s"retrieve metric errors" when {
      "a set of non-existent targets are supplied" in {
        val targets: Seq[MetricTarget] =
          loadMetricTargetCandidatesValidNonExistentInPrometheus

        withConfig() { config =>
          withAnomalyDetectionEngine() { engine =>
            withHardcodedPrometheusMetricClient() { metricClient =>
              val result =
                targets
                  .map(metricClient.getMetricValue)
                  .map(_.value)
                  .map(_.unsafeRunSync())

              val lefts = result.filter(_.isLeft)

              lefts.length mustEqual (targets.length)
            }
          }
        }
      }
    }
  }

}
