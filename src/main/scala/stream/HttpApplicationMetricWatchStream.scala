package stream
import domain.Target
import cats.effect.{ContextShift, Async, Timer, ExitCode, IO, Sync}
import cats.syntax._
import config.HttpApplicationMetricConfig
import fs2.Stream
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.duration._

class HttpApplicationMetricWatchStream(config: HttpApplicationMetricConfig)(
    implicit
    val cs: ContextShift[IO],
    timer: Timer[IO],
    sync: Sync[IO],
    async: Async[IO]
) extends MetricWatchStream {

  val log: Logger = LoggerFactory.getLogger("HttpMetricStream")

  override def runForever(watchList: Seq[Target]): IO[Unit] =
    Stream
      .emits(watchList)
      .covary[IO]
      .parEvalMapUnordered(config.parallelismMax)(process)
      .parEvalMapUnordered(config.parallelismMax)(validate)
      .repeat.metered(config.limitSeconds.seconds)
      .compile
      .drain

  private def process(target: Target): IO[Either[String, String]] = {
    log.info(s"Target ${target.value.toString}")
    IO.pure(Right(target.value))
  }

  private def validate(res: Either[String, String]): IO[Option[String]] = {
    log.info(s"Some ${res}")
    IO.pure(res.toOption)
  }
}
