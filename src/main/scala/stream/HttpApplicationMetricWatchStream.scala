package stream
import domain.Target
import cats.effect.{ContextShift, Async, Timer, IO, Sync}
import cats.syntax._
import config.{ApplicationMetricProcessingConfig, Config}
import fs2.Stream
import org.http4s.client.Client
import org.slf4j.{Logger, LoggerFactory}
import web.{MetricClient, HttpPrometheusMetricClient}

import scala.concurrent.duration._

class HttpApplicationMetricWatchStream(
    config: ApplicationMetricProcessingConfig,
    metricClient: MetricClient
)(implicit
    cs: ContextShift[IO],
    timer: Timer[IO],
    sync: Sync[IO],
    async: Async[IO]
) extends MetricWatchStream {

  val log: Logger = LoggerFactory.getLogger("HttpMetricStream")

  override def runForever(watchList: Seq[Target]): IO[Unit] =
    Stream
      .emits(watchList)
      .covary[IO]
      .parEvalMapUnordered(config.streamParallelismMax)(process)
      .parEvalMapUnordered(config.streamParallelismMax)(validate)
      .repeat
      .metered(config.streamSleepTime.seconds)
      .compile
      .drain

  private def process(target: Target): IO[Either[String, String]] = {
    metricClient.getMetricValue
  }

  private def validate(res: Either[String, String]): IO[Option[String]] = {
    log.info(s"Some ${res}")
    IO.pure(res.toOption)
  }
}

object HttpApplicationMetricWatchStream {
  def apply(config: Config, blazeClient: Client[IO])(
      implicit
      cs: ContextShift[IO],
      timer: Timer[IO],
      sync: Sync[IO],
      async: Async[IO]
  ): HttpApplicationMetricWatchStream =
    new HttpApplicationMetricWatchStream(
      config.applicationMetricProcessingConfig,
      HttpPrometheusMetricClient(config.httpConfig, blazeClient)
    )

}
