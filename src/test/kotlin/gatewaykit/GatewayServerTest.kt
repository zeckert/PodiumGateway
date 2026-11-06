package gatewaykit

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
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
}
