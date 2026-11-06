package gatewaykit

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import gatewaykit.config.ConfigLoader
import gatewaykit.config.model.GatewayConfigFile
import gatewaykit.config.model. GlobalGatewayConfig
import gatewaykit.config.model.RouteConfig
import gatewaykit.config.parseDuration
import gatewaykit.middleware.ApiKeyAuthMiddleware
import gatewaykit.pipeline.GatewayResponse
import gatewaykit.pipeline.Middleware
import gatewaykit.pipeline.Pipeline
import gatewaykit.pipeline.RequestContext
import gatewaykit.proxy.RouteMatchResult
import gatewaykit.proxy.RouteMatcher
import gatewaykit.proxy.UpstreamProxy
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors

class GatewayServer(
    private val config: GatewayConfigFile = GatewayConfigFile()
) {
    constructor(port: Int) : this(GatewayConfigFile(gateway = GlobalGatewayConfig(port = port)))

    private val startTime = Instant.now()
    private var server: HttpServer? = null

    private val routeMatcher = RouteMatcher(config.routes)
    private val upstreamProxy = UpstreamProxy(parseDuration(config.gateway.globalTimeout))
    private val routePipelines: Map<String, Pipeline> = config.routes.associate { route ->
        route.path to buildPipeline(route)
    }

    val actualPort: Int
        get() = server?.address?.port ?: config.gateway.port

    fun start() {
        val httpServer = HttpServer.create(InetSocketAddress(config.gateway.port), 0)
        httpServer.executor = Executors.newVirtualThreadPerTaskExecutor()

        httpServer.createContext("/") { exchange ->
            handleRequest(exchange)
        }

        httpServer.start()
        server = httpServer
        println("GatewayKit listening on port $actualPort")
    }

    fun stop() {
        server?.stop(0)
    }

    private fun buildPipeline(route: RouteConfig): Pipeline {
        val middlewares = mutableListOf<Middleware>()
        route.auth?.let { middlewares.add(ApiKeyAuthMiddleware(it)) }
        return Pipeline(middlewares) { ctx ->
            upstreamProxy.forward(ctx)
        }
    }

    private fun handleRequest(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val method = exchange.requestMethod

        // Core Requirement #2: GET /health always returns 200 OK regardless of config
        if (method == "GET" && path == "/health") {
            val uptime = Duration.between(startTime, Instant.now()).seconds
            sendResponse(exchange, GatewayResponse.json(200, """{"status":"healthy","uptime_seconds":$uptime}"""))
            return
        }

        when (val matchResult = routeMatcher.match(path, method)) {
            is RouteMatchResult.NotFound -> {
                sendResponse(exchange, GatewayResponse.json(404, """{"error":"not_found"}"""))
            }
            is RouteMatchResult.MethodNotAllowed -> {
                val allowedHeader = matchResult.allowedMethods.joinToString(", ")
                sendResponse(
                    exchange,
                    GatewayResponse.json(
                        status = 405,
                        jsonBody = """{"error":"method_not_allowed"}""",
                        extraHeaders = mapOf("Allow" to allowedHeader)
                    )
                )
            }
            is RouteMatchResult.Matched -> {
                val route = matchResult.route
                val headers = mutableMapOf<String, MutableList<String>>()
                exchange.requestHeaders.forEach { (k, v) ->
                    headers[k] = v.toMutableList()
                }
                val bodyBytes = exchange.requestBody.use { it.readAllBytes() }
                val clientIp = exchange.remoteAddress?.address?.hostAddress ?: "unknown"

                val ctx = RequestContext(
                    method = method,
                    rawPath = path,
                    query = exchange.requestURI.rawQuery,
                    clientIp = clientIp,
                    headers = headers,
                    body = bodyBytes,
                    route = route
                )

                val pipeline = routePipelines.getValue(route.path)
                val response = pipeline.execute(ctx)
                sendResponse(exchange, response)
            }
        }
    }

    private fun sendResponse(exchange: HttpExchange, response: GatewayResponse) {
        response.headers.forEach { (name, values) ->
            values.forEach { value ->
                exchange.responseHeaders.add(name, value)
            }
        }
        val bodySize = if (response.body.isEmpty()) -1L else response.body.size.toLong()
        exchange.sendResponseHeaders(response.status, bodySize)
        if (response.body.isNotEmpty()) {
            exchange.responseBody.use { it.write(response.body) }
        } else {
            exchange.responseBody.close()
        }
    }
}

fun main(args: Array<String>) {
    val configFile = ConfigLoader.resolveConfigFile(args)
    val config = if (configFile.exists()) {
        println("Loading configuration from ${configFile.path}")
        ConfigLoader.load(configFile)
    } else {
        println("No config file found at ${configFile.path}, starting with default settings")
        GatewayConfigFile()
    }
    GatewayServer(config).start()
}
