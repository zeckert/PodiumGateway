package gatewaykit.config.model

data class AuthConfig(
    val type: String = "api_key",
    val header: String = "X-API-Key",
    val keys: List<String> = emptyList()
)
