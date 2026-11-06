# Architecture & Engineering Decisions (`DECISIONS.md`)

## Environment & Toolchain Notes
- **JDK 21 LTS Pinning**: During initial setup, IntelliJ defaulted to an early-access OpenJDK 27 build (`27.0.1.0.3`), whose 5-segment version string triggered a `JavaVersion.parse` failure inside the Kotlin compiler daemon. Used my AI agent to help debug this and pinned the project and Gradle toolchain to **JDK 21 LTS** (giving us production-ready Virtual Threads via `Executors.newVirtualThreadPerTaskExecutor()` while keeping Gradle/Kotlin compilation deterministic). 
- I don't normally do any development on my personal Windows computer, so I spent 30 minutes getting Intellij to actually build anything with the help of the AI.

## Feature Prioritization
*(To be expanded as features are implemented)*

## Architectural Choices & Proxy Pipeline
- **Two-Phase `for`-Loop Pipeline over `foldRight` / Recursive Closures**: The first iteration that the AI agent spit out used a `foldRight` lambda composition, which was pretty slick, but wasn't really readable. I asked it for other options, and it offered recursive index-based chains (`next(ctx)`) and a more standard explicit two-phase `Middleware` interface (`onRequest` returning `GatewayResponse?` to short-circuit, and `onResponse` in reverse order) inside a plain `for` loop in `Pipeline.kt`. I went with the standard for loop because it's more immediately obvious what's happening and it's also easier to debug a loop than it is a recursive stack or a `foldRight` lambda chain, should things go wrong. For a shared project, it's more important to be readable than it is to be clever.
- **Zero-Annotation Config Data Model**: Originally, the data classes that the AI generated from the gateway.yaml were full of `@JsonProperty` and `@JsonIgnoreProperties`, which really cluttered things. I asked it why it had done that and for atlernatives, ending up with a configured `ObjectMapper` instead.It also wanted to make everything under `config` a single file, but again, that isn't as readable, and should things change for a RateLimitConfig, for example, it's much easier to modify it in its own file than when it's a single data class in a 400 line file of data classes.
## What I Would Build Next
*(To be expanded)*

## How AI Tools Were Used
- I used AI pair-programming interactively to debug the initial WSL/Windows JDK 27 vs. JDK 21 Gradle daemon mismatch, discuss the middleware/filter-chain pattern before writing code, and iterate in small, verifiable vertical slices.

