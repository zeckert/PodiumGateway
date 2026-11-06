package gatewaykit.config.model

data class RateLimitConfig(
    val requests: Int,
    val window: String,
    val strategy: String = "fixed_window",
    val per: String = "ip"
)
