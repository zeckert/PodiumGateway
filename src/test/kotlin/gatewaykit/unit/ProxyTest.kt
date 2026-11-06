package gatewaykit.unit

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sun.net.httpserver.HttpServer
import gatewaykit.GatewayServer
import gatewaykit.config.model.GatewayConfigFile
import gatewaykit.config.model.GlobalGatewayConfig
import gatewaykit.config.model.RouteConfig
import gatewaykit.config.model.UpstreamConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

class ProxyTest {
    private lateinit var server: GatewayServer
    private lateinit var mockUpstream: HttpServer
    private val client = HttpClient.newHttpClient()
    private val mapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        mockUpstream = HttpServer.create(InetSocketAddress(0), 0)
        mockUpstream.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            if (path == "/slow") {
                Thread.sleep(400)
            }
            val body = """{"received_path":"$path","method":"${exchange.requestMethod}"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        mockUpstream.start()
        val upstreamPort = mockUpstream.address.port

        val config = GatewayConfigFile(
            gateway = GlobalGatewayConfig(port = 0, globalTimeout = "5s"),
            routes = listOf(
                RouteConfig(
                    path = "/api/users",
                    methods = listOf("GET", "POST"),
                    stripPrefix = false,
                    upstream = UpstreamConfig(url = "http://localhost:$upstreamPort")
                ),
                RouteConfig(
                    path = "/api/products",
                    methods = listOf("GET"),
                    stripPrefix = true,
                    upstream = UpstreamConfig(url = "http://localhost:$upstreamPort")
                ),
                RouteConfig(
                    path = "/api/slow",
                    methods = listOf("GET"),
                    stripPrefix = true,
                    upstream = UpstreamConfig(url = "http://localhost:$upstreamPort", timeout = "100ms")
                ),
                RouteConfig(
                    path = "/api/dead",
                    methods = listOf("GET"),
                    stripPrefix = false,
                    upstream = UpstreamConfig(url = "http://localhost:1")
                )
            )
        )
        server = GatewayServer(config)
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.stop()
        mockUpstream.stop(0)
    }

    @Test
    fun `proxies matching route without stripping prefix when strip_prefix is false`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${server.actualPort}/api/users/42"))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        val json: Map<String, Any> = mapper.readValue(response.body())
        assertEquals("/api/users/42", json["received_path"])
        assertEquals("GET", json["method"])
    }

    @Test
    fun `strips route prefix when strip_prefix is true`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${server.actualPort}/api/products/123"))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        val json: Map<String, Any> = mapper.readValue(response.body())
        assertEquals("/123", json["received_path"])
    }

    @Test
    fun `disallowed HTTP method on matched route returns 405 Method Not Allowed`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${server.actualPort}/api/products/123"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(405, response.statusCode())
    }

    @Test
    fun `upstream exceeding route timeout returns 504 Gateway Timeout`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${server.actualPort}/api/slow/slow"))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(504, response.statusCode())
    }

    @Test
    fun `unreachable upstream returns 502 Bad Gateway`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:${server.actualPort}/api/dead"))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(502, response.statusCode())
    }
}
