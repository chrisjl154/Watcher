package config

case class Config(example: String, httpApplicationMetricConfig: HttpApplicationMetricConfig)

case class HttpApplicationMetricConfig(parallelismMax: Int, limitSeconds: Int)
