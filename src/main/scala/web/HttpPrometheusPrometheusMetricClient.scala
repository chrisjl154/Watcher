package web
import domain._
import cats.effect.{IO, ContextShift}
import org.http4s.client._
import config.PrometheusConfig
import domain.{MetricTarget, PrometheusQueryResult}
import io.circe.generic.auto._
import io.circe.syntax._
import cats.implicits._
import io.circe.parser._
import org.http4s.Uri.RegName
import org.http4s.{Query, Uri}
import org.slf4j.{Logger, LoggerFactory}

class HttpPrometheusPrometheusMetricClient(
    prometheusConfig: PrometheusConfig,
    blazeClient: Client[IO]
)(implicit
    val cs: ContextShift[IO]
) extends PrometheusMetricClient{
  val log: Logger =
    LoggerFactory.getLogger(HttpPrometheusPrometheusMetricClient.getClass.getName)

  override def getMetricValue(
      query: MetricTarget
  ): IO[Either[String, PrometheusQueryResult]] =
    blazeClient
      .expect[String](
        generateHttp4sUri(
          prometheusConfig.host,
          prometheusConfig.port,
          prometheusConfig.apiEndpoint,
          Map[String, Seq[String]](
            "query" -> Seq[String](query.prometheusQueryString)
          )
        )
      )
      .redeemWith(
        ex =>
          IO(
            Left(ex.getMessage)
          ), //TODO: This feels messy, is there a way to clean this up?
        res => IO(decodePrometheusResponse(res))
      )

  private def generateHttp4sUri(
      host: String,
      port: Int,
      apiEndpoint: String,
      queryString: Map[String, Seq[String]]
  ): Uri =
    Uri(
      Option(Uri.Scheme.http),
      Option(
        Uri.Authority(
          None,
          RegName(host),
          Option(port)
        )
      ),
      apiEndpoint,
      Query.fromMap(queryString),
      None
    )
  private def decodePrometheusResponse(
      resp: String
  ): Either[String, PrometheusQueryResult] =
    decode(resp)(prometheusQueryResultDecoder).leftMap(_.getMessage)
}

object HttpPrometheusPrometheusMetricClient {
  def apply(
      prometheusConfig: PrometheusConfig,
      blazeClient: Client[IO]
  )(implicit
      cs: ContextShift[IO]
  ): HttpPrometheusPrometheusMetricClient =
    new HttpPrometheusPrometheusMetricClient(prometheusConfig, blazeClient)
}
