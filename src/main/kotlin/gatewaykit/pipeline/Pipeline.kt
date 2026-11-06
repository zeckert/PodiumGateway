package gatewaykit.pipeline

class Pipeline(
    private val middlewares: List<Middleware>,
    private val upstreamCall: (RequestContext) -> GatewayResponse
) {
    fun execute(ctx: RequestContext): GatewayResponse {
        for (middleware in middlewares) {
            val earlyResponse = middleware.onRequest(ctx)
            if (earlyResponse != null) {
                return earlyResponse
            }
        }

        var response = upstreamCall(ctx)

        for (middleware in middlewares.reversed()) {
            response = middleware.onResponse(ctx, response)
        }

        return response
    }
}
