package application

import cats.effect.IO
import domain.MetricTargetCandidate

trait TargetLoader {
  def loadAll(source: String): IO[Seq[MetricTargetCandidate]]
}
