package config

case class Config(example: String, applicationMetricProcessingConfig: ApplicationMetricProcessingConfig, httpConfig: HttpConfig)

case class HttpConfig(host: String, port: String, maxConcurrentRequests: Int)

case class ApplicationMetricProcessingConfig(streamParallelismMax: Int, streamSleepTime: Int)
