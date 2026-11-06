package gatewaykit.proxy

import gatewaykit.config.model.RouteConfig
import gatewaykit.config.parseDuration
import gatewaykit.pipeline.GatewayResponse
import gatewaykit.pipeline.RequestContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

class UpstreamProxy(
    private val globalTimeout: Duration,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
) {
    companion object {
        // Java's HttpClient manages these hop-by-hop headers automatically
        private val RESTRICTED_HEADERS = setOf(
            "connection", "content-length", "expect", "host", "upgrade", "transfer-encoding", "keep-alive"
        )

        fun resolveForwardPath(rawPath: String, route: RouteConfig): String {
            if (!route.stripPrefix) return rawPath
            val prefix = if (route.path.length > 1) route.path.trimEnd('/') else route.path
            val stripped = if (prefix == "/") rawPath else rawPath.removePrefix(prefix)
            return when {
                stripped.isEmpty() -> "/"
                stripped.startsWith("/") -> stripped
                else -> "/$stripped"
            }
        }
    }

    fun forward(ctx: RequestContext, targetBaseUrl: String? = null): GatewayResponse {
        val baseUrl = (targetBaseUrl
            ?: ctx.route.upstream.url
            ?: ctx.route.upstream.targets?.firstOrNull()?.url
            ?: return GatewayResponse.json(502, """{"error":"bad_gateway","message":"No upstream target configured"}"""))
            .trimEnd('/')

        val forwardPath = resolveForwardPath(ctx.rawPath, ctx.route)
        val fullUrl = buildString {
            append(baseUrl)
            append(forwardPath)
            if (!ctx.query.isNullOrBlank()) {
                append("?")
                append(ctx.query)
            }
        }

        val timeout = ctx.route.upstream.timeout?.let { parseDuration(it) } ?: globalTimeout

        val bodyPublisher = if (ctx.body.isEmpty() && (ctx.method == "GET" || ctx.method == "HEAD" || ctx.method == "DELETE")) {
            HttpRequest.BodyPublishers.noBody()
        } else {
            HttpRequest.BodyPublishers.ofByteArray(ctx.body)
        }

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(fullUrl))
            .timeout(timeout)
            .method(ctx.method, bodyPublisher)

        ctx.headers.forEach { (name, values) ->
            if (name.lowercase() !in RESTRICTED_HEADERS) {
                values.forEach { value ->
                    requestBuilder.header(name, value)
                }
            }
        }

        val request = requestBuilder.build()
        val retryConfig = ctx.route.upstream.retry
        val maxAttempts = (retryConfig?.attempts ?: 1).coerceAtLeast(1)
        val retryableStatuses = retryConfig?.on?.toSet() ?: emptySet()
        val baseDelayMillis = retryConfig?.let { parseDuration(it.initialDelay).toMillis() } ?: 0L

        var lastResponse: GatewayResponse? = null
        for (attempt in 1..maxAttempts) {
            val response = executeSingleAttempt(request)
            lastResponse = response

            val shouldRetry = attempt < maxAttempts && response.status in retryableStatuses
            if (!shouldRetry) {
                return response
            }

            if (baseDelayMillis > 0) {
                val delayMillis = if (retryConfig?.backoff.equals("exponential", ignoreCase = true)) {
                    baseDelayMillis * (1L shl (attempt - 1))
                } else {
                    baseDelayMillis
                }
                Thread.sleep(delayMillis)
            }
        }

        return lastResponse ?: GatewayResponse.json(502, """{"error":"bad_gateway"}""")
    }

    private fun executeSingleAttempt(request: HttpRequest): GatewayResponse {
        return try {
            val upstreamResponse = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray()
            )

            val responseHeaders = mutableMapOf<String, MutableList<String>>()
            upstreamResponse.headers().map().forEach { (k, v) ->
                if (k.lowercase() !in setOf("transfer-encoding", "content-length", "connection") && !k.startsWith(":")) {
                    responseHeaders[k] = v.toMutableList()
                }
            }

            GatewayResponse(
                status = upstreamResponse.statusCode(),
                headers = responseHeaders,
                body = upstreamResponse.body()
            )
        } catch (e: HttpTimeoutException) {
            GatewayResponse.json(504, """{"error":"gateway_timeout"}""")
        } catch (e: Exception) {
            GatewayResponse.json(502, """{"error":"bad_gateway"}""")
        }
    }
}
