package gatewaykit.pipeline

import gatewaykit.config.model.RouteConfig
import java.time.Instant

data class RequestContext(
    val method: String,
    val rawPath: String,
    val query: String?,
    val clientIp: String,
    val headers: MutableMap<String, MutableList<String>>,
    var body: ByteArray,
    val route: RouteConfig,
    val requestStartTime: Instant = Instant.now()
)
