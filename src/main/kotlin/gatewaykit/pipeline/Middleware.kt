package gatewaykit.pipeline

interface Middleware {
    /**
     * Runs before forwarding to upstream.
     * Return null to continue to the next middleware, or return a GatewayResponse
     * (e.g. 401, 429, 503) to short-circuit and return immediately.
     */
    fun onRequest(ctx: RequestContext): GatewayResponse? = null

    /**
     * Runs after receiving the upstream response.
     * Can inspect the status (e.g. Circuit Breaker) or transform headers/body.
     */
    fun onResponse(ctx: RequestContext, response: GatewayResponse): GatewayResponse = response
}
