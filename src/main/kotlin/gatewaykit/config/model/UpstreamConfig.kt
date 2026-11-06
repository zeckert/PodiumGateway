package gatewaykit.config.model

data class UpstreamConfig(
    val url: String? = null,
    val targets: List<UpstreamTargetConfig>? = null,
    val balance: String? = null,
    val timeout: String? = null,
    val retry: RetryConfig? = null,
    val healthCheck: HealthCheckConfig? = null
)

data class UpstreamTargetConfig(
    val url: String,
    val weight: Int = 1
)

data class RetryConfig(
    val attempts: Int = 1,
    val backoff: String = "fixed",
    val initialDelay: String = "1s",
    val on: List<Int> = emptyList()
)

data class HealthCheckConfig(
    val path: String = "/healthz",
    val interval: String = "30s",
    val unhealthyThreshold: Int = 3
)
