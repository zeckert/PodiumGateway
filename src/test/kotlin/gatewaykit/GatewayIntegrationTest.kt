package gatewaykit

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sun.net.httpserver.HttpServer
import gatewaykit.config.ConfigLoader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

class GatewayIntegrationTest {
    private val upstreamServers = mutableListOf<HttpServer>()
    private lateinit var gateway: GatewayServer
    private val client = HttpClient.newHttpClient()
    private val mapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        // Start mock upstream servers matching the ports in gateway.yaml (3001..3006)
        val portsToService = mapOf(
            3001 to "users-service",
            3002 to "orders-service",
            3003 to "products-service-1",
            3004 to "products-service-2",
            3005 to "legacy-service",
            3006 to "internal-service"
        )

        portsToService.forEach { (port, serviceName) ->
            val server = HttpServer.create(InetSocketAddress(port), 0)
            server.createContext("/") { exchange ->
                val requestBody = exchange.requestBody.use { it.readAllBytes().toString(Charsets.UTF_8) }
                val responseJson = """
                    {
                      "service": "$serviceName",
                      "method": "${exchange.requestMethod}",
                      "path": "${exchange.requestURI.path}",
                      "received_body": ${if (requestBody.isNotBlank()) requestBody else "null"}
                    }
                """.trimIndent().toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.responseHeaders.add("Server", "MockUpstream/1.0")
                exchange.responseHeaders.add("X-Powered-By", "Kotlin")
                exchange.sendResponseHeaders(200, responseJson.size.toLong())
                exchange.responseBody.use { it.write(responseJson) }
            }
            server.start()
            upstreamServers.add(server)
        }

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
        upstreamServers.forEach { it.stop(0) }
        upstreamServers.clear()
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
    }
}
