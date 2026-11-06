package gatewaykit.config.model

data class GatewayConfigFile(
    val gateway: GlobalGatewayConfig = GlobalGatewayConfig(),
    val routes: List<RouteConfig> = emptyList()
)

data class GlobalGatewayConfig(
    val port: Int = 8080,
    val globalTimeout: String = "30s",
    val globalRateLimit: RateLimitConfig? = null
)
