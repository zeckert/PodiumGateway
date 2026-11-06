package gatewaykit

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors

class GatewayServer(private val port: Int = 8080) {
    private val startTime = Instant.now()
    private var server: HttpServer? = null

    val actualPort: Int
        get() = server?.address?.port ?: port

    fun start() {
        val httpServer = HttpServer.create(InetSocketAddress(port), 0)
        httpServer.executor = Executors.newVirtualThreadPerTaskExecutor()

        httpServer.createContext("/") { exchange ->
            handleRequest(exchange)
        }

        httpServer.start()
        server = httpServer
        println("GatewayKit listening on port $port")
    }

    fun stop() {
        server?.stop(0)
    }

    private fun handleRequest(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val method = exchange.requestMethod

        // Core Requirement #2: GET /health always returns 200 OK
        if (method == "GET" && path == "/health") {
            val uptime = Duration.between(startTime, Instant.now()).seconds
            val body = """{"status":"healthy","uptime_seconds":$uptime}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            return
        }

        // Everything else returns 404 for now
        val notFound = """{"error":"not_found"}""".toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(404, notFound.size.toLong())
        exchange.responseBody.use { it.write(notFound) }
    }
}

fun main() {
    GatewayServer(8080).start()
}
