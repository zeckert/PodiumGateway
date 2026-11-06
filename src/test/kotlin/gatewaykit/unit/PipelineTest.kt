package gatewaykit.unit

import gatewaykit.config.model.RouteConfig
import gatewaykit.config.model.UpstreamConfig
import gatewaykit.pipeline.GatewayResponse
import gatewaykit.pipeline.Middleware
import gatewaykit.pipeline.Pipeline
import gatewaykit.pipeline.RequestContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PipelineTest {
    private fun dummyContext(): RequestContext {
        val route = RouteConfig(
            path = "/api/test",
            methods = listOf("GET"),
            upstream = UpstreamConfig(url = "http://localhost:3000")
        )
        return RequestContext(
            method = "GET",
            rawPath = "/api/test",
            query = null,
            clientIp = "127.0.0.1",
            headers = mutableMapOf(),
            body = ByteArray(0),
            route = route
        )
    }

    @Test
    fun `executes onRequest in order, then upstream, then onResponse in reverse order`() {
        val events = mutableListOf<String>()

        val first = object : Middleware {
            override fun onRequest(ctx: RequestContext): GatewayResponse? {
                events.add("first:onRequest")
                return null
            }

            override fun onResponse(ctx: RequestContext, response: GatewayResponse): GatewayResponse {
                events.add("first:onResponse")
                return response
            }
        }

        val second = object : Middleware {
            override fun onRequest(ctx: RequestContext): GatewayResponse? {
                events.add("second:onRequest")
                return null
            }

            override fun onResponse(ctx: RequestContext, response: GatewayResponse): GatewayResponse {
                events.add("second:onResponse")
                return response
            }
        }

        val pipeline = Pipeline(listOf(first, second)) {
            events.add("upstream")
            GatewayResponse.json(200, """{"ok":true}""")
        }

        val response = pipeline.execute(dummyContext())

        assertEquals(200, response.status)
        assertEquals(
            listOf("first:onRequest", "second:onRequest", "upstream", "second:onResponse", "first:onResponse"),
            events
        )
    }

    @Test
    fun `short-circuits immediately when onRequest returns non-null response`() {
        var secondCalled = false
        var upstreamCalled = false

        val blockingMiddleware = object : Middleware {
            override fun onRequest(ctx: RequestContext): GatewayResponse {
                return GatewayResponse.json(401, """{"error":"unauthorized"}""")
            }
        }

        val secondMiddleware = object : Middleware {
            override fun onRequest(ctx: RequestContext): GatewayResponse? {
                secondCalled = true
                return null
            }
        }

        val pipeline = Pipeline(listOf(blockingMiddleware, secondMiddleware)) {
            upstreamCalled = true
            GatewayResponse.json(200, """{"ok":true}""")
        }

        val response = pipeline.execute(dummyContext())

        assertEquals(401, response.status)
        assertFalse(secondCalled)
        assertFalse(upstreamCalled)
    }
}
