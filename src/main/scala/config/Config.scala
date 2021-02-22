package config

case class Config(
    applicationMetricProcessingConfig: ApplicationMetricProcessingConfig,
    httpConfig: HttpConfig,
    prometheusConfig: PrometheusConfig,
    targetDefinitions: TargetDefinitions,
    kafkaConfig: KafkaConfig
)

case class HttpConfig(maxConcurrentRequests: Int)

case class ApplicationMetricProcessingConfig(
    streamParallelismMax: Int,
    streamSleepTime: Int
)

case class PrometheusConfig(host: String, port: Int, apiEndpoint: String)

case class TargetDefinitions(source: String)

case class KafkaConfig(
    bootstrapServer: String,
    consumerGroup: String,
    anomalyTopic: String
)
