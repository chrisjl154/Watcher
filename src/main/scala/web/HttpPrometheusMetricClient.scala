package web
import cats.effect.{IO, ContextShift}
import org.http4s.client._
import config.PrometheusConfig
import domain.{PrometheusQueryResult, PrometheusQuery}
import io.circe.generic.auto._
import io.circe.syntax._

class HttpPrometheusMetricClient(
    prometheusConfig: PrometheusConfig,
    blazeClient: Client[IO]
)(implicit
    val cs: ContextShift[IO]
) {
  val prometheusApiEndpoint =  "api/v1/query"

  def getMetricValue(
      query: PrometheusQuery
  ): IO[Either[String, PrometheusQueryResult]] =
    blazeClient
      .expect[String](
        s"http://${prometheusConfig.host}:${prometheusConfig.port}/${prometheusApiEndpoint}?query=${query}"
      )
      .redeemWith(
        ex =>
          IO.pure(
            Left(ex.getMessage)
          ), //TODO: This feels messy, is there a way to clean this up?
        res => IO (decodePrometheusResponse(res))
      )

  private def decodePrometheusResponse(
      resp: String
  ): Either[String, PrometheusQueryResult] =
    resp.asJson
      .as[PrometheusQueryResult]
      .toOption
      .toRight[String]("Error decoding result from query to Prometheus")

}

object HttpPrometheusMetricClient {
  def apply(
      prometheusConfig: PrometheusConfig,
      blazeClient: Client[IO]
  )(implicit
      cs: ContextShift[IO]
  ): HttpPrometheusMetricClient =
    new HttpPrometheusMetricClient(prometheusConfig, blazeClient)
}
