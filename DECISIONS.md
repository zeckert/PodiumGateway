# Architecture & Engineering Decisions (`DECISIONS.md`)

## Environment & Toolchain Notes
- **JDK 21 LTS Pinning**: During initial setup, IntelliJ defaulted to an early-access OpenJDK 27 build (`27.0.1.0.3`), whose 5-segment version string triggered a `JavaVersion.parse` failure inside the Kotlin compiler daemon. Used my AI agent to help debug this and pinned the project and Gradle toolchain to **JDK 21 LTS** (giving us production-ready Virtual Threads via `Executors.newVirtualThreadPerTaskExecutor()` while keeping Gradle/Kotlin compilation deterministic). 
- I don't normally do any development on my personal Windows computer, so I spent 30 minutes getting Intellij to actually build anything.

## Feature Prioritization
*(To be expanded as features are implemented)*

## Architectural Choices & Proxy Pipeline
*(To be expanded as pipeline middlewares are added)*

## What We Would Build Next
*(To be expanded)*

## How AI Tools Were Used
- Used AI pair-programming interactively to debug the initial WSL/Windows JDK 27 vs. JDK 21 Gradle daemon mismatch, discuss the middleware/filter-chain pattern before writing code, and iterate in small, verifiable vertical slices.

