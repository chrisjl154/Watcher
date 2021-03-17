package anomaly

case class AnomalyMessage(targetAppName: String, metricResult: String, function: String, meta: AnomalyMessageMetaData)

case class AnomalyMessageMetaData(timeDetectedMillis: String)
