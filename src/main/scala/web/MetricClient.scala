package web

import cats.effect.IO

trait MetricClient {
  def getMetricValue: IO[Either[String, String]] //TODO: Facilitate the use of generic types for Right
}
