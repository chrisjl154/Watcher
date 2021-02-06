package web
import cats.effect.IO
import domain.{MetricTarget, PrometheusData, PrometheusQueryResult, Result}

class HardcodedPrometheusMetricClient extends PrometheusMetricClient {
  override def getMetricValue(
      query: MetricTarget
  ): IO[Either[String, PrometheusQueryResult]] = {
    IO {
      if (query.name.equals("valid"))
        Right(
          PrometheusQueryResult(
            "success",
            PrometheusData("vector", List[Result](Result(List[String]("100"))))
          )
        )
      else Left("Error")
    }
  }
}

object HardcodedPrometheusMetricClient {
  def apply(): HardcodedPrometheusMetricClient =
    new HardcodedPrometheusMetricClient()
}
