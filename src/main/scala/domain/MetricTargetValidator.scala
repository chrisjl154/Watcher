package domain
import cats.data._
import cats.data.Validated._
import cats.implicits._

object MetricTargetValidator {
  type ValidationResult[A] = ValidatedNec[MetricTargetValidationError, A]

  def apply(
      name: String,
      prometheusQueryString: String,
      threshold: String
  ): ValidationResult[MetricTarget] =
    (
      validateQueryString(prometheusQueryString),
      validateQueryName(name),
      validateThreshold(threshold)
    ).mapN(MetricTarget)

  private def validateQueryString(query: String): ValidationResult[String] =
    if (query.isEmpty) InvalidQueryString.invalidNec else query.validNec

  private def validateQueryName(name: String): ValidationResult[String] =
    if (name.isEmpty) InvalidQueryName.invalidNec else name.validNec

  private def validateThreshold(threshold: String): ValidationResult[String] =
    threshold.validNec
}

sealed trait MetricTargetValidationError {
  def error: String
}

case object InvalidQueryString extends MetricTargetValidationError {
  def error: String = "An invalid Prometheus query string was supplied"
}

case object InvalidQueryName extends MetricTargetValidationError {
  def error: String = "An invalid query name was specified"
}
