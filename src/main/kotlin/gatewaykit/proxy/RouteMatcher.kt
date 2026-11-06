package gatewaykit.proxy

import gatewaykit.config.model.RouteConfig

sealed interface RouteMatchResult {
    data class Matched(val route: RouteConfig) : RouteMatchResult
    data class MethodNotAllowed(val allowedMethods: List<String>) : RouteMatchResult
    data object NotFound : RouteMatchResult
}

class RouteMatcher(routes: List<RouteConfig>) {
    // Sort by longest path first so more specific prefixes match before broader ones
    private val sortedRoutes = routes.sortedByDescending { it.path.length }

    fun match(requestPath: String, method: String): RouteMatchResult {
        val normalizedPath = if (requestPath.length > 1) requestPath.trimEnd('/') else requestPath

        val matchingRoute = sortedRoutes.firstOrNull { route ->
            val routePath = if (route.path.length > 1) route.path.trimEnd('/') else route.path
            if (routePath == "/") {
                true
            } else {
                normalizedPath == routePath || normalizedPath.startsWith("$routePath/")
            }
        } ?: return RouteMatchResult.NotFound

        val methodAllowed = matchingRoute.methods.any { it.equals(method, ignoreCase = true) }
        return if (methodAllowed) {
            RouteMatchResult.Matched(matchingRoute)
        } else {
            RouteMatchResult.MethodNotAllowed(matchingRoute.methods)
        }
    }
}
