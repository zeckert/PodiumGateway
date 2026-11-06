# Architecture & Engineering Decisions (`DECISIONS.md`)

## Environment & Toolchain Notes
- I don't normally do any development on my personal Windows computer, so I spent 30 minutes getting IntelliJ to actually build anything with the help of the AI. I spent some time beforehand setting up the WSL and getting Ubuntu and IntelliJ downloaded, but I didn't have my Java env set up properly. It was breaking on the OpenJDK 27 build (`27.0.1.0.3`) when I tried to actually run the server. I used the AI to help me debug that, eventually pinning the project to Java 21, but it continued to fail because the intelliJ gradle daemon was still using the broken version. 
- There was also an issue with the WSL and Windows fighting over file locks in the .gradle cache folder. I used the AI to help debug that as well, and it recognized the issue and explained it. The solution was to use a separate `project-cache-dir` inside WSL.

## Feature Prioritization
- The core features and unit tests were the first thing I wanted to finish, for obvious reasons. 
- After the core gateway features, I added the integration test to handle scripting out the actual calls to upstream.
- I then went and added a middleware implementation. I chose the API Key Auth because it was the simplest one to implement and show the implementation pattern. 
- Then I added the retry logic in the `UpstreamProxy` because it was an outlier from the middleware operations. It is on the proxy so that it doesn't re-run the middleware steps. A scenario exists where you'd maybe want your retries to be swept up in the rate limiting or circuit breaking logic, but you wouldn't want something like Auth to be done again for a retried request. There is always going to be a balance to strike with the intersection of circuit breakers and rate limiters and retry logic, and I fall on the side that retries probably shouldn't get swept up in the resilience. Obviously that requires the retry logic to be somewhat bounded. I could definitely see that coming up if the config were such that you could spawn infinite retries, and it didn't have a solid backoff.

## Architectural Choices 
- I decided to do a middleware pipeline for things like auth and rate limiting and circuit breakers. It's a middleware interface that allows you to implement the different handlers you need. That way it is easy to add or remove things like circuit breakers and rate limiters and retry logic.
- The first iteration that the AI agent spit out for the middleware pipeline used a `foldRight` lambda composition, which was pretty slick, but wasn't really readable. I asked it for other options, and it offered recursive index-based chains (`next(ctx)`) and a more standard explicit two-phase `Middleware` interface (`onRequest` returning `GatewayResponse?` to short-circuit, and `onResponse` in reverse order) inside a plain `for` loop in `Pipeline.kt`. I went with the standard for loop because it's more immediately obvious what's happening. It's also easier to debug a loop than it is a recursive stack or a `foldRight` lambda chain, should things go wrong. For a shared project, it's more important to be readable than it is to be clever.
- Originally, the data classes that the AI generated from the gateway.yaml were full of `@JsonProperty` and `@JsonIgnoreProperties`, which really cluttered things. I asked it why it had done that and for alternatives, ending up with a configured `ObjectMapper` instead. It also wanted to make everything under `config` a single file, but again, that isn't as readable, and should things change for a RateLimitConfig, for example, it's much easier to modify it in its own file than when it's a single data class in a 400 line file of data classes.

## Partially implemented features
- All of the yaml config classes (`RateLimitConfig`, `CircuitBreakerConfig`, `TransformConfig` etc) and their startup validations in `ConfigLoader` are done and tested. I just didn't get to actually implement the rate limiter and circuit breaker.
- Routes that have multiple upstream targets currently just forward to the first one, so the routes still work even though load balancing hasn't been implemented. 
  
## What I Would Build Next
- Next I'd work my way through the rate limiting. I'd use a `ConcurrentHashMap` with the buckets keyed by route + client IP (with per bucket synchronization so a bunch of concurrent requests on a rate limited route don't end up racing)
- A circuit breaker would be the next thing. I'd have hooked into `onRequest` for short-circuiting when it was open, and `onResponse` to record failures in the rolling window.
- Round robin load balancing and health checks would have come next in the `UpstreamProxy`.
- Finally, the Request/Response transformations would have been done last. They have a lot of edge cases, and I didn't want to get stuck in a quagmire while I barely have anything done. 
  
## How AI Tools Were Used

- I really just chat with it like it's a person. I ask it questions and help build out my mental model of the project and what needs done. Then I work through those things in fairly small steps. When I don't understand something from the spec, or something that it's said, I'll ask it to explain. Then I'll give it an example to make sure I understand what it's saying. As I build out the mental model, I discuss what needs to be done, in order, to get through things. The AI agents will kind of make a running to-do list that I follow. I work through the list with the agent and correct code and ask it to rework as needed, like when it tried to make the giant model file instead of splitting them out. 

