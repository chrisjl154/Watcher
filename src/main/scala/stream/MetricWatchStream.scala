package stream

import cats.effect.{IO, ExitCode}
import domain.Target

trait MetricWatchStream {
  def runForever(watchList: Seq[Target]): IO[Unit]
}
