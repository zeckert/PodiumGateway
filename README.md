# GatewayKit

A lightweight, configuration-driven HTTP API gateway built in Kotlin using standard library HTTP primitives (`com.sun.net.httpserver.HttpServer` and `java.net.http.HttpClient`) and Jackson YAML parsing.

## Prerequisites & Dependencies

- **JDK 21+** (uses Virtual Threads via `Executors.newVirtualThreadPerTaskExecutor()`; Gradle's `foojay-resolver` plugin will automatically provision JDK 21 if not already installed)
- **Gradle Wrapper** (included in the repository via `./gradlew` and `gradlew.bat`)
- **Runtime Libraries**:
  - `jackson-databind`, `jackson-module-kotlin`, `jackson-dataformat-yaml` (YAML configuration parsing & JSON serialization)
- **Test Libraries**:
  - `kotlin-test`, `junit-jupiter` (JUnit 5)

## How to Set Up and Run the Gateway

### 1. Run with default `gateway.yaml` in project root
```bash
./gradlew run
```

### 2. Run with a custom configuration file path
You can pass the YAML file path either as a command-line argument or via the `GATEWAY_CONFIG` environment variable:

```bash
# Via command-line argument
./gradlew run --args="/path/to/custom-gateway.yaml"

# Or via environment variable
GATEWAY_CONFIG=/path/to/custom-gateway.yaml ./gradlew run
```

### 3. Verify the Health Endpoint
Once running, the admin health check is always available at `GET /health` regardless of route configuration:
```bash
curl http://localhost:8080/health
# {"status":"healthy","uptime_seconds":4}
```

### 4. Optional: Start the Standalone Mock Upstreams (`3001`–`3006`)
For manual testing against `gateway.yaml`, you can run the `main()` method in `src/test/kotlin/gatewaykit/mock/MockUpstreamCluster.kt` from IntelliJ to spin up mock upstream services on ports `3001` through `3006` (supporting `/healthz`, `/slow`, `/flaky`, and `/missing`).

---

## How to Run the Tests

The test suite (unit tests + self-contained integration tests with embedded mock upstream servers) runs with a single command:

```bash
./gradlew test
```

---

## Implemented Configuration Features Checklist

### Core Requirements (Baseline)
- [x] **Configurable Server Port** (`gateway.port`, defaults to `8080`)
- [x] **CLI & Environment Config Loading** (CLI arg or `GATEWAY_CONFIG` env var, with schema & duration validation)
- [x] **Always-On Health Endpoint** (`GET /health` -> `200 OK` with `{"status": "healthy", "uptime_seconds": <int>}`)
- [x] **Route Prefix Matching & Forwarding** (longest-prefix matching, `404 Not Found` on unmatched routes, upstream status/header/body pass-through)
- [x] **Prefix Stripping** (`strip_prefix: true` strips the matched route prefix before forwarding to upstream)
- [x] **HTTP Method Filtering** (`methods` allowlist per route; returns `405 Method Not Allowed` with `Allow` header when disallowed)

### Resilience, Security & Traffic Management
- [x] **Global & Per-Route Timeouts** (`gateway.global_timeout` and per-route `upstream.timeout` override; returns `504 Gateway Timeout` on timeout and `502 Bad Gateway` when upstream is unreachable)
- [x] **Upstream Retries & Backoff** (`upstream.retry.attempts`, `fixed` and `exponential` backoff, `initial_delay`, and status code matching `on: [502, 503, 504]`)
- [x] **API Key Authentication Middleware** (`auth.type: "api_key"`, configurable `header` and allowed `keys`, returning `401 Unauthorized` when missing/invalid)

### Parsed & Validated in Config Model (Next Up in Pipeline)
The YAML schema models (`gatewaykit.config.model.*`) and `ConfigLoader` validation fully parse and validate the remaining `gateway.yaml` sections so they can be dropped in as `Middleware` implementations:
- [ ] **Rate Limiting** (`global_rate_limit` and per-route `rate_limit` with `fixed_window` / `sliding_window`)
- [ ] **Circuit Breaker** (`circuit_breaker` with `threshold`, `window`, `cooldown`)
- [ ] **Multi-Target Load Balancing & Active Health Checks** (`upstream.targets` weighted round-robin & `health_check`; currently routes to the primary target)
- [ ] **Request & Response Header/Body Transformations** (`request_transform` and `response_transform`)
