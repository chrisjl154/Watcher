package application
import cats.effect.IO
import domain.{MetricTarget, MetricTargetCandidate}
import io.circe.parser.decode
import org.slf4j.{LoggerFactory, Logger}

import scala.io.Source

object HardcodedTargetLoader extends TargetLoader {
  private val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  /**
   * Loads targets from a resource
   * @param source Path to the resource
   * @return
   */
  override def loadAll(source: String): IO[Seq[MetricTargetCandidate]] =
    IO {
      Source
        .fromResource(source)
        .getLines()
        .map(decode[MetricTargetCandidate](_))
        .toSeq
        .filter(_.isRight)
        .map(item => item.toOption.get)
    }
}
