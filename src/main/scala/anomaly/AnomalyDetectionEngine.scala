package anomaly

import cats.effect.IO
import domain.MetricTarget
import org.slf4j.{LoggerFactory, Logger}

class AnomalyDetectionEngine {
  private val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  private def scaleOutCheck(res: Float, threshold: Float): Boolean =
    threshold < res
  private def scaleInCheck(res: Float, threshold: Float): Boolean =
    res < threshold

  def detect(
      target: MetricTarget,
      metricResult: Float
  ): Option[AnomalyMessage] = {
    val check: Option[Boolean] = target.function match {
      case "in" =>
        Option(scaleInCheck(metricResult, target.threshold.toFloat))
      case "out" =>
        Option(scaleOutCheck(metricResult, target.threshold.toFloat))
      case _ => {
        log.error(
          s"${target.function} is not a valid scaling function. Will not scale."
        )
        None
      }
    }

    check match {
      case Some(checkFunction) => {
        if (checkFunction) {
          log.info(
            s"Anomaly detected for \'${target.name}\'. Threshold: \'${target.threshold}\' Result: \'${metricResult}\' Function: \'${target.function}\'"
          )
          Option(
            AnomalyMessage(
              target.appName,
              metricResult.toString,
              target.function,
              AnomalyMessageMetaData(System.currentTimeMillis.toString)
            )
          )
        } else None
      }
      case None => None
    }
  }
}

object AnomalyDetectionEngine {
  def apply(): AnomalyDetectionEngine = new AnomalyDetectionEngine()
}
