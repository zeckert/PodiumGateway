package gatewaykit.unit

import gatewaykit.config.model.AuthConfig
import gatewaykit.config.model.RouteConfig
import gatewaykit.config.model.UpstreamConfig
import gatewaykit.middleware.ApiKeyAuthMiddleware
import gatewaykit.pipeline.RequestContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ApiKeyAuthMiddlewareTest {
    private val authConfig = AuthConfig(
        type = "api_key",
        header = "X-API-Key",
        keys = listOf("sk_live_abc123", "sk_live_def456")
    )
    private val middleware = ApiKeyAuthMiddleware(authConfig)

    private fun createContext(headers: Map<String, List<String>> = emptyMap()): RequestContext {
        val mutableHeaders = headers.mapValues { it.value.toMutableList() }.toMutableMap()
        val route = RouteConfig(
            path = "/api/internal",
            methods = listOf("GET"),
            upstream = UpstreamConfig(url = "http://localhost:3006"),
            auth = authConfig
        )
        return RequestContext(
            method = "GET",
            rawPath = "/api/internal",
            query = null,
            clientIp = "127.0.0.1",
            headers = mutableHeaders,
            body = ByteArray(0),
            route = route
        )
    }

    @Test
    fun `returns 401 when API key header is missing`() {
        val response = middleware.onRequest(createContext())
        assertNotNull(response)
        assertEquals(401, response.status)
    }

    @Test
    fun `returns 401 when API key is not in allowed keys`() {
        val response = middleware.onRequest(createContext(mapOf("X-API-Key" to listOf("invalid_key"))))
        assertNotNull(response)
        assertEquals(401, response.status)
    }

    @Test
    fun `returns null to continue pipeline when API key is valid`() {
        val response = middleware.onRequest(createContext(mapOf("X-API-Key" to listOf("sk_live_abc123"))))
        assertNull(response)
    }

    @Test
    fun `matches API key header name case-insensitively`() {
        val response = middleware.onRequest(createContext(mapOf("x-api-key" to listOf("sk_live_def456"))))
        assertNull(response)
    }
}
