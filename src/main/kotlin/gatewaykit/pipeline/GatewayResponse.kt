package gatewaykit.pipeline

data class GatewayResponse(
    var status: Int,
    val headers: MutableMap<String, MutableList<String>> = mutableMapOf(),
    var body: ByteArray = ByteArray(0)
) {
    companion object {
        fun json(status: Int, jsonBody: String, extraHeaders: Map<String, String> = emptyMap()): GatewayResponse {
            val headers = mutableMapOf<String, MutableList<String>>(
                "Content-Type" to mutableListOf("application/json")
            )
            extraHeaders.forEach { (k, v) -> headers[k] = mutableListOf(v) }
            return GatewayResponse(
                status = status,
                headers = headers,
                body = jsonBody.toByteArray(Charsets.UTF_8)
            )
        }
    }
}
