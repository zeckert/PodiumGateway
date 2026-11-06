package gatewaykit.mock

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MockUpstreamCluster {
    private val servers = mutableListOf<HttpServer>()
    val flakyRequestCounter = AtomicInteger(0)

    fun startAll() {
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
            server.executor = Executors.newVirtualThreadPerTaskExecutor()
            server.createContext("/") { exchange ->
                val path = exchange.requestURI.path

                if (path == "/healthz") {
                    val ok = """{"status":"ok"}""".toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, ok.size.toLong())
                    exchange.responseBody.use { it.write(ok) }
                    return@createContext
                }

                if (path.endsWith("/slow")) {
                    Thread.sleep(6000)
                }

                if (path.endsWith("/flaky")) {
                    val attempt = flakyRequestCounter.incrementAndGet()
                    if (attempt % 3 != 0) {
                        val err = """{"service":"$serviceName","error":"temporary_failure","attempt":$attempt}""".toByteArray()
                        exchange.responseHeaders.add("Content-Type", "application/json")
                        exchange.sendResponseHeaders(503, err.size.toLong())
                        exchange.responseBody.use { it.write(err) }
                        return@createContext
                    }
                }

                if (path.endsWith("/missing")) {
                    val notFoundJson = """{"service":"$serviceName","error":"not_found_in_upstream"}""".toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(404, notFoundJson.size.toLong())
                    exchange.responseBody.use { it.write(notFoundJson) }
                    return@createContext
                }

                val requestBody = exchange.requestBody.use { it.readAllBytes().toString(Charsets.UTF_8) }
                val responseJson = """
                    {
                      "service": "$serviceName",
                      "method": "${exchange.requestMethod}",
                      "path": "$path",
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
            servers.add(server)
        }
    }

    fun stopAll() {
        servers.forEach { it.stop(0) }
        servers.clear()
    }
}

fun main() {
    val cluster = MockUpstreamCluster()
    cluster.startAll()
    println("Mock upstream services listening on ports 3001..3006 (/healthz, /slow, /flaky, /missing supported)")
}
