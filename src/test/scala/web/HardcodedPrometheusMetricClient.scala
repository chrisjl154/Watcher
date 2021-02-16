package web
import cats.data.EitherT
import cats.effect.IO
import domain.{MetricTarget, PrometheusData, PrometheusQueryResult, Result}
import test.TestSupport._

import scala.util.Random

class HardcodedPrometheusMetricClient extends PrometheusMetricClient {
  private val allowedQueries = loadValidTargetQueryNames

  override def getMetricValue(
      query: MetricTarget
  ): EitherT[IO, String, PrometheusQueryResult] =
    EitherT(IO {
      if (allowedQueries.contains(query.name))
        Right(
          PrometheusQueryResult(
            "success",
            PrometheusData(
              "vector",
              List[Result](
                Result(
                  List[String](Random.nextInt().toString, math.pow(query.threshold.toFloat, 2).toString)
                )
              )
            )
          )
        )
      else Left("Error")
    })
}

object HardcodedPrometheusMetricClient {
  def apply(): HardcodedPrometheusMetricClient =
    new HardcodedPrometheusMetricClient()
}
