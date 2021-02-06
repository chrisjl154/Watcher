package domain

case class PrometheusQueryResult(status: String, data: PrometheusData)

case class PrometheusData(resultType: String, result: List[Result])

case class Result(
    value: Seq[String]
)
