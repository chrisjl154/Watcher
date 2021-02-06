package stream

import cats.effect.IO
import domain.MetricTarget

trait MetricWatchStream {
  def runForever(watchList: Seq[MetricTarget]): IO[Unit]
}
