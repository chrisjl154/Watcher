package anomaly

import cats.effect.IO
import domain.MetricTarget
import org.slf4j.{LoggerFactory, Logger}

class AnomalyDetectionEngine {
  def detect(
      target: MetricTarget,
      metricResult: Float
  ): Option[AnomalyMessage] = {
    val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

    //TODO: Unsafe casting here, fix it!
    if (target.threshold.toFloat < metricResult) {
      log.info(
        s"Anomaly detected for \'${target.name}\'. Threshold: \'${target.threshold}\' Result: \'${metricResult}\'"
      )
      Some(
        AnomalyMessage(
          target.appName,
          metricResult.toString,
          AnomalyMessageMetaData(System.currentTimeMillis.toString)
        )
      )
    } else None
  }
}

object AnomalyDetectionEngine {
  def apply(): AnomalyDetectionEngine = new AnomalyDetectionEngine()
}
