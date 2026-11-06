package gatewaykit.config.model

data class CircuitBreakerConfig(
    val threshold: Int = 5,
    val window: String = "60s",
    val cooldown: String = "30s"
)
