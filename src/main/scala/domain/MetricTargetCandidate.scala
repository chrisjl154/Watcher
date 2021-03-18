package domain

case class MetricTargetCandidate(
    proposedName: String,
    proposedPrometheusQueryString: String,
    proposedThreshold: String,
    description: String,
    proposedAppName: String,
    function: String
)
