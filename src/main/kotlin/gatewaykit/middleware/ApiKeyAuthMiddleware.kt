package gatewaykit.middleware

import gatewaykit.config.model.AuthConfig
import gatewaykit.pipeline.GatewayResponse
import gatewaykit.pipeline.Middleware
import gatewaykit.pipeline.RequestContext

class ApiKeyAuthMiddleware(authConfig: AuthConfig) : Middleware {
    private val headerName = authConfig.header
    private val validKeys = authConfig.keys.toSet()

    override fun onRequest(ctx: RequestContext): GatewayResponse? {
        val providedKey = ctx.headers.entries
            .firstOrNull { it.key.equals(headerName, ignoreCase = true) }
            ?.value
            ?.firstOrNull()

        if (providedKey == null || providedKey !in validKeys) {
            return GatewayResponse.json(401, """{"error":"unauthorized"}""")
        }
        return null
    }
}
