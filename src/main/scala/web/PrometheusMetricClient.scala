package web

import cats.data.EitherT
import cats.effect.IO
import domain.{MetricTarget, PrometheusQueryResult}

trait PrometheusMetricClient {
  def getMetricValue(
      query: MetricTarget
  ): EitherT[IO, String, PrometheusQueryResult]
}
