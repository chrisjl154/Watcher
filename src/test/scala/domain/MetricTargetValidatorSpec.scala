package domain

import cats.Parallel
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.ExecutionContext
import org.scalatest.concurrent.Eventually
import org.scalatest.wordspec.AnyWordSpec
import cats.effect.{IO, ContextShift}
import domain.MetricTargetValidator.ValidationResult
import test.TestSupport._

class MetricTargetValidatorSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with BeforeAndAfterAll {
  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  private implicit val parallel: Parallel[IO] = IO.ioParallel

  s"${MetricTargetValidator.getClass.getName}" should {
    "Show no validation errors" when {
      "All parameters passed are correct" in {
        val validCandidates = loadMetricTargetCandidatesValid

        val result: Seq[ValidationResult[MetricTarget]] =
          validCandidates.map(candidate =>
            MetricTargetValidator(
              candidate.proposedName,
              candidate.proposedPrometheusQueryString,
              candidate.proposedThreshold
            )
          )

        val validEntries = result.filter(_.isValid)

        validEntries.size mustEqual (validCandidates.size)
      }
    }

    //TODO: Expand this as we add proper validation rules
    "Accumulate errors" when {
      "An invalid query string is provided" in {
        val invalidCandidate =
          MetricTargetValidatorSpec.candidateInvalidQueryString

        val result: ValidationResult[MetricTarget] =
          MetricTargetValidator(
            invalidCandidate.proposedName,
            invalidCandidate.proposedPrometheusQueryString,
            invalidCandidate.proposedThreshold
          )

        val failuresList: List[MetricTargetValidationError] =
          result.toEither.left
            .getOrElse(fail(s"Expected a Left of MetricTargetValidationError"))
            .toNonEmptyList
            .toList

        failuresList must contain(InvalidQueryString)
      }
      "An invalid query name is provided" in {
        val invalidCandidate =
          MetricTargetValidatorSpec.candidateInvalidName

        val result: ValidationResult[MetricTarget] =
          MetricTargetValidator(
            invalidCandidate.proposedName,
            invalidCandidate.proposedPrometheusQueryString,
            invalidCandidate.proposedThreshold
          )

        val failuresList: List[MetricTargetValidationError] =
          result.toEither.left
            .getOrElse(fail(s"Expected a Left of MetricTargetValidationError"))
            .toNonEmptyList
            .toList

        failuresList must contain(InvalidQueryName)

      }
    }
  }
}

object MetricTargetValidatorSpec {
  val candidateInvalidQueryString: MetricTargetCandidate =
    MetricTargetCandidate(
      "InvalidQueryOne",
      "",
      "20",
      "An invalid query string which is empty"
    )

  val candidateInvalidName: MetricTargetCandidate =
    MetricTargetCandidate(
      "",
      "istio_request_total{app=\"adservice\"}",
      "20",
      "An invalid name"
    )
}
