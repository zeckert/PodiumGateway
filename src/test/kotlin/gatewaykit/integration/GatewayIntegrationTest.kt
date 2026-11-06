package gatewaykit.integration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import gatewaykit.GatewayServer
import gatewaykit.config.ConfigLoader
import gatewaykit.mock.MockUpstreamCluster
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

class GatewayIntegrationTest {
    private val upstreamCluster = MockUpstreamCluster()
    private lateinit var gateway: GatewayServer
    private val client = HttpClient.newHttpClient()
    private val mapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        upstreamCluster.startAll()

        // Load the actual gateway.yaml from the repo root (using port 0 so test doesn't clash if 8080 is in use)
        val loadedConfig = ConfigLoader.load(File("gateway.yaml"))
        val testConfig = loadedConfig.copy(
            gateway = loadedConfig.gateway.copy(port = 0)
        )
        gateway = GatewayServer(testConfig)
        gateway.start()
    }

    @AfterEach
    fun tearDown() {
        gateway.stop()
        upstreamCluster.stopAll()
    }

    @Test
    fun `end-to-end routing using gateway yaml across configured services`() {
        // 1. /api/users (strip_prefix: false -> /api/users/99 reaches users-service)
        val usersRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:${gateway.actualPort}/api/users/99"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, usersRes.statusCode())
        val usersBody: Map<String, Any?> = mapper.readValue(usersRes.body())
        assertEquals("users-service", usersBody["service"])
        assertEquals("/api/users/99", usersBody["path"])

        // 2. /api/products (strip_prefix: true -> /api/products/123 becomes /123)
        val productsRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:${gateway.actualPort}/api/products/123"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, productsRes.statusCode())
        val productsBody: Map<String, Any?> = mapper.readValue(productsRes.body())
        assertEquals("/123", productsBody["path"])

        // 3. Disallowed method on /api/products (POST -> 405)
        val disallowedRes = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:${gateway.actualPort}/api/products/123"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(405, disallowedRes.statusCode())

        // 4. Upstream 404 on matched route (/api/users/missing -> 404 from users-service)
        val upstream404Res = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:${gateway.actualPort}/api/users/missing"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(404, upstream404Res.statusCode())
        val upstream404Body: Map<String, Any?> = mapper.readValue(upstream404Res.body())
        assertEquals("users-service", upstream404Body["service"])
        assertEquals("not_found_in_upstream", upstream404Body["error"])
    }

    @Test
    fun `api_key auth on internal route rejects missing or invalid key and allows valid key`() {
        val internalUrl = URI.create("http://localhost:${gateway.actualPort}/api/internal/metrics")

        // Missing header -> 401
        val missingKeyRes = client.send(
            HttpRequest.newBuilder().uri(internalUrl).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(401, missingKeyRes.statusCode())

        // Wrong key -> 401
        val wrongKeyRes = client.send(
            HttpRequest.newBuilder()
                .uri(internalUrl)
                .header("X-API-Key", "wrong_key")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(401, wrongKeyRes.statusCode())

        // Valid key from gateway.yaml -> 200 OK
        val validKeyRes = client.send(
            HttpRequest.newBuilder()
                .uri(internalUrl)
                .header("X-API-Key", "sk_live_abc123")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(200, validKeyRes.statusCode())
    }

    @Test
    fun `orders route retries on 503 and succeeds on third attempt`() {
        val ordersFlakyUrl = URI.create("http://localhost:${gateway.actualPort}/api/orders/flaky")

        val response = client.send(
            HttpRequest.newBuilder().uri(ordersFlakyUrl).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )

        assertEquals(200, response.statusCode())
        assertEquals(3, upstreamCluster.flakyRequestCounter.get())
    }
}
