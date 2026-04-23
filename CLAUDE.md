# lovely-micronaut

Shared Micronaut library utilities. Published as `com.lovelysystems:lovely-micronaut`.

## Build

- `./gradlew build` runs tests, detekt, and Kover.
- Kover enforces 91% instruction coverage (`build.gradle.kts` → `kover { ... }`).
  New main code needs matching tests or the build fails.

## Testing (Kotest 5)

- `beforeSpec` / `afterSpec` must be declared at the spec root, not inside nested
  `context(...)` blocks. Nested hooks silently do not fire.
- Log-assertion idiom: Logback `ListAppender` attached to a package-level logger, cast
  from SLF4J `Logger` to `ch.qos.logback.classic.Logger`. See
  `src/test/kotlin/ls/http/RequestIdFilterTest.kt` and
  `src/test/kotlin/ls/coroutines/CachedSuspendingTest.kt`.

## Logging

- Declare at file-top-level: `private val logger = KotlinLogging.logger {}` (oshai
  `io.github.oshai:kotlin-logging-jvm`).
- Top-level loggers resolve to the file's class name with the `Kt` suffix stripped
  (e.g. `CachedSuspending.kt` → `ls.coroutines.CachedSuspending`). Relevant when
  configuring log filters or attaching test appenders.
