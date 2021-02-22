package stream

import cats.effect.{IO, ExitCode}
import domain.MetricTarget

trait MetricWatchStream {
  def runForever(watchList: Seq[MetricTarget]): IO[ExitCode]
}
