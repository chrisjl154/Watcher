package web
import cats.data.EitherT
import domain._
import cats.effect.{IO, ContextShift}
import org.http4s.client._
import config.PrometheusConfig
import domain.{MetricTarget, PrometheusQueryResult}
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax._
import cats.implicits._
import org.http4s.Uri.RegName
import org.http4s.{Query, Uri}
import org.slf4j.{Logger, LoggerFactory}

class HttpPrometheusPrometheusMetricClient(
    prometheusConfig: PrometheusConfig,
    blazeClient: Client[IO]
)(implicit
    val cs: ContextShift[IO]
) extends PrometheusMetricClient {
  val log: Logger =
    LoggerFactory.getLogger(
      HttpPrometheusPrometheusMetricClient.getClass.getName
    )

  /**
   * Retrieves a metric value using a query from the metric store
   * @param query
   * @return
   */
  override def getMetricValue(
      query: MetricTarget
  ): EitherT[IO, String, PrometheusQueryResult] =
    EitherT( //Wrap the results in a side-effect capturing Either
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
        .redeemWith( //Handle errors using redeem
          ex =>
            IO(
              Left(ex.getMessage)
            ),
          res => IO(decodePrometheusResponse(res)) //Wrap the decided response in an IO
        )
    )

  /**
   * We need to manually construct the URI for Http4S due to some changes in the scheme etc we need to add
   * @param host
   * @param port
   * @param apiEndpoint
   * @param queryString
   * @return
   */
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
      Query.fromMap(queryString), //generate a query string using the map
      None
    )

  /**
   * Decodes a JSON response from the HTTP API
   * @param resp
   * @return
   */
  private def decodePrometheusResponse(
      resp: String
  ): Either[String, PrometheusQueryResult] =
    decode(resp)(prometheusQueryResultDecoder).leftMap(_.getMessage) //Map errors in an Either
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
