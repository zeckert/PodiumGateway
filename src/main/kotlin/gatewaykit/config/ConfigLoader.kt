package gatewaykit.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import gatewaykit.config.model.GatewayConfigFile
import gatewaykit.config.model.RateLimitConfig
import java.io.File
import java.time.Duration

object ConfigLoader {
    private val yamlMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun load(file: File): GatewayConfigFile {
        require(file.exists() && file.isFile) { "Configuration file not found: ${file.path}" }
        return parse(file.readText())
    }

    fun parse(yamlContent: String): GatewayConfigFile {
        require(yamlContent.isNotBlank()) { "Configuration YAML cannot be blank" }
        val config: GatewayConfigFile = try {
            yamlMapper.readValue(yamlContent)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse gateway YAML configuration: ${e.message}", e)
        }
        validate(config)
        return config
    }

    fun resolveConfigFile(args: Array<String>): File {
        val path = args.firstOrNull { it.isNotBlank() }
            ?: System.getenv("GATEWAY_CONFIG")?.takeIf { it.isNotBlank() }
            ?: "gateway.yaml"
        return File(path)
    }

    private fun validate(config: GatewayConfigFile) {
        require(config.gateway.port in 0..65535) { "Invalid gateway port: ${config.gateway.port}" }
        parseDuration(config.gateway.globalTimeout)
        config.gateway.globalRateLimit?.let { validateRateLimit(it) }

        config.routes.forEach { route ->
            require(route.path.startsWith("/")) { "Route path must start with '/': ${route.path}" }
            require(route.methods.isNotEmpty()) { "Route '${route.path}' must specify at least one HTTP method" }
            val hasSingleUrl = !route.upstream.url.isNullOrBlank()
            val hasTargets = !route.upstream.targets.isNullOrEmpty()
            require(hasSingleUrl || hasTargets) {
                "Route '${route.path}' must define either upstream.url or upstream.targets"
            }
            route.upstream.timeout?.let { parseDuration(it) }
            route.upstream.retry?.let {
                require(it.attempts >= 1) { "Retry attempts must be >= 1 for route '${route.path}'" }
                parseDuration(it.initialDelay)
            }
            route.rateLimit?.let { validateRateLimit(it) }
            route.circuitBreaker?.let {
                require(it.threshold >= 1) { "Circuit breaker threshold must be >= 1 for route '${route.path}'" }
                parseDuration(it.window)
                parseDuration(it.cooldown)
            }
        }
    }

    private fun validateRateLimit(rateLimit: RateLimitConfig) {
        require(rateLimit.requests > 0) { "Rate limit requests must be > 0" }
        parseDuration(rateLimit.window)
    }
}

fun parseDuration(raw: String): Duration {
    val trimmed = raw.trim().lowercase()
    return try {
        when {
            trimmed.endsWith("ms") -> Duration.ofMillis(trimmed.removeSuffix("ms").toLong())
            trimmed.endsWith("s") -> Duration.ofSeconds(trimmed.removeSuffix("s").toLong())
            trimmed.endsWith("m") -> Duration.ofMinutes(trimmed.removeSuffix("m").toLong())
            trimmed.endsWith("h") -> Duration.ofHours(trimmed.removeSuffix("h").toLong())
            else -> throw IllegalArgumentException("Unsupported duration unit in '$raw'")
        }
    } catch (e: NumberFormatException) {
        throw IllegalArgumentException("Invalid duration number in '$raw'", e)
    }
}
