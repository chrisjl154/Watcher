package config

case class Config(
    example: String,
    applicationMetricProcessingConfig: ApplicationMetricProcessingConfig,
    httpConfig: HttpConfig,
    prometheusConfig: PrometheusConfig
)

case class HttpConfig(maxConcurrentRequests: Int)

case class ApplicationMetricProcessingConfig(
    streamParallelismMax: Int,
    streamSleepTime: Int
)

case class PrometheusConfig(host: String, port: String)
