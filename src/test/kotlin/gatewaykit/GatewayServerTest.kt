package gatewaykit

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import gatewaykit.config.ConfigLoader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GatewayServerTest {
    private lateinit var server: GatewayServer
    private val client = HttpClient.newHttpClient()
    private val mapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        // Use port 0 so the OS assigns an open ephemeral port (won't clash if 8080 is running)
        server = GatewayServer(0)
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `GET health returns 200 OK with healthy status and uptime_seconds`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${server.actualPort}/health"))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        val json: Map<String, Any> = mapper.readValue(response.body())
        assertEquals("healthy", json["status"])
        assertTrue(json["uptime_seconds"] is Int || json["uptime_seconds"] is Long)
        assertTrue((json["uptime_seconds"] as Number).toLong() >= 0)
    }

    @Test
    fun `unmatched route returns 404 Not Found`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${server.actualPort}/unknown-route"))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(404, response.statusCode())
    }

    @Test
    fun `ConfigLoader parses gateway yaml with all routes and nested settings`() {
        val config = ConfigLoader.load(File("gateway.yaml"))

        assertEquals(8080, config.gateway.port)
        assertEquals("30s", config.gateway.globalTimeout)
        assertEquals(100, config.gateway.globalRateLimit?.requests)
        assertEquals(5, config.routes.size)

        val productsRoute = config.routes.first { it.path == "/api/products" }
        assertTrue(productsRoute.stripPrefix)
        assertEquals(2, productsRoute.upstream.targets?.size)
        assertEquals("weighted_round_robin", productsRoute.upstream.balance)

        val legacyRoute = config.routes.first { it.path == "/api/legacy" }
        assertNotNull(legacyRoute.requestTransform)
        assertNotNull(legacyRoute.responseTransform)

        val internalRoute = config.routes.first { it.path == "/api/internal" }
        assertEquals("X-API-Key", internalRoute.auth?.header)
        assertEquals(5, internalRoute.circuitBreaker?.threshold)
    }

    @Test
    fun `ConfigLoader rejects malformed yaml or missing upstream`() {
        assertThrows<IllegalArgumentException> {
            ConfigLoader.parse("""
                routes:
                  - path: "/broken"
                    methods: ["GET"]
                    upstream: {}
            """.trimIndent())
        }
    }
}
