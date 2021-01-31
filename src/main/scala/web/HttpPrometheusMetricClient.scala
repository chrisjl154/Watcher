package web
import cats.effect.{IO, ContextShift}
import org.http4s.client._
import config.HttpConfig

class HttpPrometheusMetricClient(conf: HttpConfig, blazeClient: Client[IO])(
    implicit val cs: ContextShift[IO]
) extends MetricClient {
  override def getMetricValue: IO[Either[String, String]] = {
    blazeClient
      .expect[String](s"${conf.host}:${conf.port}/")
      .redeemWith(
        ex => IO.pure(Left(ex.getMessage)),
        res => IO.pure(Right(res))
      )
  }
}

object HttpPrometheusMetricClient {
  def apply(conf: HttpConfig, blazeClient: Client[IO])(implicit
      cs: ContextShift[IO]
  ): HttpPrometheusMetricClient =
    new HttpPrometheusMetricClient(conf, blazeClient)
}
