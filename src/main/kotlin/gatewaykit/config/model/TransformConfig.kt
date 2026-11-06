package gatewaykit.config.model

data class RequestTransformConfig(
    val headers: HeaderTransformConfig? = null,
    val body: RequestBodyTransformConfig? = null
)

data class ResponseTransformConfig(
    val headers: HeaderTransformConfig? = null,
    val body: ResponseBodyTransformConfig? = null
)

data class HeaderTransformConfig(
    val add: Map<String, String> = emptyMap(),
    val remove: List<String> = emptyList()
)

data class RequestBodyTransformConfig(
    val mapping: Map<String, String> = emptyMap()
)

data class ResponseBodyTransformConfig(
    val envelope: Map<String, Any> = emptyMap()
)
