package web
import cats.effect.{IO, ContextShift}
import org.http4s.client._
import config.PrometheusConfig
import domain.{MetricTarget, PrometheusQueryResult}
import io.circe.generic.auto._
import io.circe.syntax._
import org.slf4j.{LoggerFactory, Logger}

class HttpPrometheusMetricClient(
    prometheusConfig: PrometheusConfig,
    blazeClient: Client[IO]
)(implicit
    val cs: ContextShift[IO]
) {
  val log: Logger =
    LoggerFactory.getLogger(HttpPrometheusMetricClient.getClass.getName)
  val prometheusApiEndpoint = "api/v1/query"

  def getMetricValue(
                      query: MetricTarget
                    ): IO[Either[String, PrometheusQueryResult]] =
    blazeClient
      .expect[String](
        s"http://${prometheusConfig.host}:${prometheusConfig.port}/${prometheusApiEndpoint}?query=${query.prometheusQueryString}"
      )
      .redeemWith(
        ex =>
          IO {
            log.error(s"Error with query ${query.prometheusQueryString}")
            log.error(ex.getMessage)
            Left(ex.getMessage)
          }, //TODO: This feels messy, is there a way to clean this up?
        res => IO(decodePrometheusResponse(res))
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
