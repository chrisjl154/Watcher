package domain
import cats.data._
import cats.data.Validated._
import cats.implicits._
import domain.MetricTargetValidator.ValidationResult
import org.slf4j.{LoggerFactory, Logger}

case class ValidatedMetricTarget(
    name: String,
    result: ValidationResult[MetricTarget]
)

object MetricTargetValidator {
  private val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  type ValidationResult[A] = ValidatedNec[MetricTargetValidationError, A]

  def apply(
      name: String,
      prometheusQueryString: String,
      threshold: String,
      appName: String,
      function: String
  ): ValidationResult[MetricTarget] =
    (
      validateQueryName(name),
      validateQueryString(prometheusQueryString),
      validateThreshold(threshold),
      validateApplicationName(appName),
      validateFunctionString(function)
    ).mapN(MetricTarget)

  private def validateFunctionString(
      function: String
  ): ValidationResult[String] =
    if (function.equals("in") || function.equals("out")) function.validNec
    else InvalidFunctionValue.invalidNec

  private def validateQueryString(query: String): ValidationResult[String] =
    if (query.isEmpty) InvalidQueryString.invalidNec else query.validNec

  private def validateQueryName(name: String): ValidationResult[String] =
    if (name.isEmpty) InvalidQueryName.invalidNec else name.validNec

  private def validateThreshold(threshold: String): ValidationResult[String] =
    threshold.validNec

  private def validateApplicationName(
      appName: String
  ): ValidationResult[String] = appName.validNec

  def validateAll(
      targets: Seq[MetricTargetCandidate]
  ): Seq[MetricTarget] = {
    val validated =
      targets
        .map(candidate =>
          ValidatedMetricTarget(
            candidate.proposedName,
            MetricTargetValidator(
              candidate.proposedName,
              candidate.proposedPrometheusQueryString,
              candidate.proposedThreshold,
              candidate.proposedAppName,
              candidate.function
            )
          )
        )

    val successfulValidations: Seq[ValidatedMetricTarget] =
      validated.filter(_.result.isValid)

    val failures: Seq[ValidatedMetricTarget] =
      validated.filterNot(_.result.isValid)

    failures
      .foreach { failureRes =>
        failureRes.result.toEither.leftMap { errs =>
          errs.toList.foreach(err =>
            log.error(
              s"Error validating \'${failureRes.name}\': ${err.error}"
            )
          )
        }
      }

    for {
      validatedOptions <- successfulValidations.map(_.result.toOption)
      validatedItems = validatedOptions.get
    } yield (validatedItems)
  }
}

sealed trait MetricTargetValidationError {
  def error: String
}

case object UnidentifiedQueryError extends MetricTargetValidationError {
  override def error: String =
    "An unexpected error occurred formatting this query. See system logs. "
}

case object InvalidQueryString extends MetricTargetValidationError {
  override def error: String = "An invalid Prometheus query string was supplied"
}

case object InvalidQueryName extends MetricTargetValidationError {
  override def error: String = "An invalid query name was specified"
}
case object InvalidFunctionValue extends MetricTargetValidationError {
  override def error: String =
    "An invalid function value was specified. Can be \'in\' or \'out\'"
}
