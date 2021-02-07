package web

import cats.effect.IO
import domain.{PrometheusQueryResult, MetricTarget}

trait PrometheusMetricClient {
  def getMetricValue(
      query: MetricTarget
  ): IO[Either[String, PrometheusQueryResult]]
}
