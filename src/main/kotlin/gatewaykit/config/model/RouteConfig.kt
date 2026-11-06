package gatewaykit.config.model

data class RouteConfig(
    val path: String,
    val methods: List<String> = emptyList(),
    val stripPrefix: Boolean = false,
    val upstream: UpstreamConfig,
    val rateLimit: RateLimitConfig? = null,
    val auth: AuthConfig? = null,
    val circuitBreaker: CircuitBreakerConfig? = null,
    val requestTransform: RequestTransformConfig? = null,
    val responseTransform: ResponseTransformConfig? = null
)
