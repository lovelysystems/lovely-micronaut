---
title: "fix: CachedSuspending truly refreshes in the background"
type: fix
status: active
date: 2026-04-22
---

# fix: CachedSuspending truly refreshes in the background

## Overview

`ls.coroutines.CachedSuspending` is documented as "always returns stale data and renews
in the background", but the current implementation blocks the caller on refresh, reads
shared state without memory-ordering guarantees, lets concurrent expiry hits stampede
`block()`, and leaks background-refresh exceptions back to the caller. This plan rewrites
the class to deliver the documented contract without changing its public API.

---

## Problem Frame

Current behavior in `src/main/kotlin/ls/coroutines/CachedSuspending.kt`:

- `coroutineScope { launch { renew() } }` — `coroutineScope` joins its children, so every
  TTL expiry still waits for a full synchronous refresh on the caller's path.
- `state: T?` and `expires: Instant` are written under `mutex` but read outside it, with
  no `@Volatile`/atomics — readers have no JMM guarantee of seeing the latest values.
- `renew()` has no double-checked guard; N concurrent expired callers each run `block()`
  in sequence under the mutex.
- A throwing `block()` inside the background `launch` propagates through `coroutineScope`
  back out of `invoke()`, defeating the stale-data-on-failure guarantee.

Downstream impact in `op-api` (per issue OP-15224):

- **High** — `AirshipWatchedEventStream` asset-durations cache runs inside a Kafka Streams
  `aggregate { runBlocking { ... } }`; every TTL expiry causes predictable latency spikes
  and can fault the stream task on a transient DB error.
- **Medium-high** — `SwisscomPaymentClient` OAuth bearer cache serializes all concurrent
  callers through renew at every TTL boundary; a fetch failure surfaces on the live
  payment path (no retry filter).
- **Medium** — `AirshipApiClient` OAuth2 token cache has the same stampede; retries mask
  the symptom but waste OAuth round-trips.
- **Low** — `RequestBasedProfileInfo` is `@RequestScope`, so the broken path is unreached.

---

## Requirements Trace

- R1. On expiry, `invoke()` returns the stale value immediately; refresh runs off the
  caller's execution path.
- R2. At most one refresh of `block()` is in flight at any time; additional callers do
  not trigger extra invocations.
- R3. Background-refresh failures are caught and logged; callers continue to receive the
  stale value until a later refresh succeeds.
- R4. `state` and `expires` are safe to read from any thread under the JMM (no torn
  reads, no stale visibility).
- R5. The public API is unchanged — constructor signature
  `CachedSuspending(maxAge: Duration, block: suspend () -> T)` and `suspend operator
  fun invoke(): T` stay identical so existing call sites need no edits.
- R6. Cold start (no value yet) still suspends and returns the freshly computed value;
  concurrent cold-start callers share a single `block()` invocation.

---

## Scope Boundaries

- No new public API surface (no `invalidate()`, no `close()`, no metrics hooks).
- No lifecycle/shutdown mechanism for the internal scope — `CachedSuspending` remains an
  application-singleton utility.
- No changes to downstream `op-api` call sites. The fix lands in `lovely-micronaut`
  and consumers pick it up on version bump.
- No port of `RequestBasedProfileInfo` to a different primitive (`LazySuspend` or
  similar) — that's an `op-api` concern, not this library's.

### Deferred to Follow-Up Work

- `op-api` version bump and regression verification on the four known call sites — done
  in `op-api` after this library is released.

---

## Context & Research

### Relevant Code and Patterns

- `src/main/kotlin/ls/coroutines/CachedSuspending.kt` — the file being rewritten.
- `src/test/kotlin/ls/coroutines/CachedSuspendingTest.kt` — existing kotest `ShouldSpec`
  suite; extend with the new behavioral coverage. Uses `kotlinx.coroutines.delay` for
  timing-based scenarios.
- `src/test/kotlin/ls/http/RequestIdFilterTest.kt` — shows the project idiom for
  asserting on log output via a Logback `ListAppender` attached to a named logger, which
  is how we verify "failures are logged not rethrown" in U2.
- `build.gradle.kts` — `io.github.oshai:kotlin-logging-jvm` is declared under
  `testImplementation` only; U1 promotes it to `implementation` (kotlin-logging is the
  team idiom, used in tests already).

### Institutional Learnings

- None found — `docs/solutions/` does not exist in this repo.

### External References

- None required. The design uses standard `kotlinx.coroutines` primitives (`Mutex`,
  `SupervisorJob`, `CoroutineScope`, `Dispatchers.Default`) with well-known semantics.

---

## Key Technical Decisions

- **Single atomic snapshot holder instead of two fields.** Replace the `state: T?` +
  `expires: Instant` pair with a single `@Volatile private var snapshot: Snapshot<T>?`
  where `Snapshot` is a local immutable data class carrying `(value, expires)`. Readers
  get a consistent pair from one volatile load; writers publish atomically via reference
  assignment. Fixes R4 with minimal ceremony.

- **Long-lived internal `CoroutineScope(SupervisorJob() + Dispatchers.Default)`.** The
  background refresh must run off the caller's path, which requires a scope the caller
  does not own. Options considered:
  - *Accept a `CoroutineScope` parameter* — would break R5 (public API) and force every
    call site to plumb a scope.
  - *`GlobalScope`* — works but unconventional; lint-discouraged.
  - *Internal scope with `SupervisorJob`* — chosen. Survives individual refresh failures,
    does not cascade cancellation to the cache, no lifecycle change on the public API.
  `Dispatchers.Default` is the neutral choice; `block()` itself can hop to
  `Dispatchers.IO` internally if the caller needs it (matches how existing consumers
  wrap DB/HTTP work).

- **Single-flight refresh via `AtomicBoolean` sentinel.** The background-refresh launch
  is gated on `refreshing.compareAndSet(false, true)`; the `finally` block resets it.
  The gate sits outside the mutex so that contended expiries short-circuit without
  queueing on the lock. The mutex stays inside the launched coroutine as a belt-and-
  braces guard for the cold-start path.

- **Cold-start path stays synchronous under the mutex.** When `snapshot == null` there
  is no stale value to return, so the first call must suspend until `block()` completes.
  Concurrent cold-start callers share that call via `mutex.withLock { snapshot?.value ?:
  computeAndStore() }` — the double-checked read inside the lock handles the case where
  caller N entered the lock after caller 1 already populated the snapshot.

- **Catch-log-swallow for background refresh only.** The `try/catch` lives inside the
  launched coroutine so exceptions do not escape. Cold-start failures still propagate
  (R6 — there is nothing stale to return). `CancellationException` is re-thrown so
  structured cancellation remains correct. Failures do not advance `expires`, so the
  next call re-triggers a refresh attempt.

- **Promote `kotlin-logging` to `implementation` dependency.** Needed so the main
  source can log warnings on refresh failure (R3). `io.github.oshai:kotlin-logging-jvm`
  is already on the classpath for tests and is the idiomatic choice in this codebase;
  SLF4J is transitively available via Micronaut but kotlin-logging is preferred for
  consistency.

---

## Open Questions

### Resolved During Planning

- *Which scope hosts background refresh launches?* Internal
  `CoroutineScope(SupervisorJob() + Dispatchers.Default)` owned by the cache instance.
- *Do we need a shutdown/close?* No — current call sites are application singletons and
  no consumer requested it. Keeping the API minimal.
- *Logging library?* `io.github.oshai.kotlinlogging.KotlinLogging` (existing team idiom).

### Deferred to Implementation

- *Exact name and placement of the log message / logger name.* Use `KotlinLogging {}`
  at file top, warn-level with the throwable; tune the wording during implementation
  and assert on substring in tests.
- *Timing tolerances inside the new tests* (`delay` durations, poll intervals). Pick
  values that stay reliable on CI while keeping the suite fast — land during implementation.

---

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not
> implementation specification. The implementing agent should treat it as context, not
> code to reproduce.*

```kotlin
class CachedSuspending<T : Any>(
    private val maxAge: Duration,
    private val block: suspend () -> T,
) {
    private data class Snapshot<T>(val value: T, val expires: Instant)

    @Volatile private var snapshot: Snapshot<T>? = null
    private val mutex = Mutex()
    private val refreshing = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend operator fun invoke(): T {
        val current = snapshot
        if (current == null) return coldStart()
        if (current.expires.isBefore(Instant.now())) triggerBackgroundRefresh()
        return current.value
    }

    private suspend fun coldStart(): T = mutex.withLock {
        snapshot?.value ?: computeAndStore()
    }

    private fun triggerBackgroundRefresh() {
        if (!refreshing.compareAndSet(false, true)) return
        scope.launch {
            try {
                mutex.withLock { computeAndStore() }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                log.warn(t) { "CachedSuspending background refresh failed" }
            } finally {
                refreshing.set(false)
            }
        }
    }

    private suspend fun computeAndStore(): T {
        val value = block()
        snapshot = Snapshot(value, Instant.now() + maxAge)
        return value
    }
}
```

State machine:

```
            ┌─────────────┐
            │ snapshot=null │
            └──────┬──────┘
         first invoke() (suspends)
                   ▼
            ┌─────────────┐
     ┌────▶ │   fresh     │ ───── invoke() within TTL ─────┐
     │      └──────┬──────┘                                │
     │        expiry detected                              │
     │      invoke() returns stale,                        │
     │      launches bg refresh                            │
     │             ▼                                       │
     │      ┌─────────────┐    block() success             │
     │      │ stale +      │ ──────────────────────────────┘
     │      │ refresh inflt│
     │      └──────┬──────┘
     │         block() throws
     │             ▼
     │      ┌─────────────┐
     └──────│ stale, log  │
            │ warning,     │
            │ keep stale   │
            └─────────────┘
```

---

## Implementation Units

- [ ] U1. **Rewrite `CachedSuspending` implementation and wire logging**

**Goal:** Replace the broken state management and refresh logic with the design above;
make `kotlin-logging` available to main sources.

**Requirements:** R1, R2, R3, R4, R5, R6

**Dependencies:** none

**Files:**
- Modify: `src/main/kotlin/ls/coroutines/CachedSuspending.kt`
- Modify: `build.gradle.kts` (promote `kotlin-logging` dep from `testImplementation` to
  `implementation`)

**Approach:**
- Introduce private `data class Snapshot<T>(value: T, expires: Instant)`.
- Replace `state`/`expires` fields with a single `@Volatile var snapshot: Snapshot<T>?`.
- Add a long-lived `CoroutineScope(SupervisorJob() + Dispatchers.Default)` instance field.
- Add `AtomicBoolean refreshing` instance field.
- `invoke()` becomes: read `snapshot`; if null, delegate to `coldStart()`; else if
  expired, `triggerBackgroundRefresh()` (non-suspending), then return stale value.
- `coldStart()` takes the mutex and re-checks `snapshot` before calling the compute
  helper so concurrent cold-start callers share the refresh.
- `triggerBackgroundRefresh()` uses `refreshing.compareAndSet(false, true)` to gate
  the launch; on success, `scope.launch` wraps the mutex-guarded refresh in
  `try / catch CancellationException (rethrow) / catch Throwable (log warn) / finally
  (reset refreshing)`.
- File-level `private val log = KotlinLogging.logger {}` using
  `io.github.oshai.kotlinlogging.KotlinLogging`.

**Patterns to follow:**
- Logger declaration idiom from `src/test/kotlin/ls/http/RequestIdFilterTest.kt`
  (`KotlinLogging.logger {}`).
- Keep class-level KDoc aligned with the new contract; remove the current note that
  `block` is "executed with an exclusive lock when a refresh is required, so no
  concurrent calls will never happen" — that was inaccurate under the old stampede,
  and the new contract is different.

**Test scenarios:** *(covered by U2 — this unit ships the implementation that U2's
tests exercise)*

**Verification:**
- Project compiles (`./gradlew build`).
- No breaking API change — compiling `CachedSuspending(Duration.ZERO) { 1 }().let { ... }`
  still works with unchanged consumer code.

---

- [ ] U2. **Extend `CachedSuspendingTest` with behavioral coverage**

**Goal:** Lock in the new contract (R1–R6) with tests that would have caught the
original bugs.

**Requirements:** R1, R2, R3, R4, R6

**Dependencies:** U1

**Files:**
- Modify: `src/test/kotlin/ls/coroutines/CachedSuspendingTest.kt`

**Approach:**
- Keep the two existing contexts unchanged — they remain valid smoke coverage.
- Add new `context(...)` blocks per scenario listed below. Use a logback `ListAppender`
  attached to the `CachedSuspending` logger for the failure-is-logged scenario, mirroring
  `RequestIdFilterTest.kt`.
- Share a small `suspend fun awaitEventually(timeout: Duration, check: suspend () -> Boolean)`
  helper inline in the spec for polling-with-timeout where a fixed `delay` would be flaky.

**Execution note:** Write or sketch these tests before finalizing U1's code — they
double as a design check for the rewrite.

**Patterns to follow:**
- `ShouldSpec` structure and `delay(...)` usage from the existing
  `CachedSuspendingTest.kt`.
- Logback `ListAppender` + `Logger.addAppender` pattern from
  `src/test/kotlin/ls/http/RequestIdFilterTest.kt`.

**Test scenarios:**

- Happy path — *Caller unblocked on expiry (R1)*: `block()` sleeps 500ms; populate the
  cache; wait until expiry; measure the second `invoke()` — it must return the stale
  value within a small window (e.g. <50ms) even though the background refresh is still
  running. After polling, the next `invoke()` returns the fresh value.

- Happy path — *Single-flight refresh at expiry (R2)*: `block()` increments a counter
  and briefly delays; populate; wait for expiry; launch N concurrent `invoke()`
  coroutines via `awaitAll`; assert the counter advances by exactly 1 (one new refresh),
  not N.

- Happy path — *Single-flight cold start (R6 + R2)*: counter-based `block()`; launch N
  concurrent first callers; assert counter == 1 and every caller got the same value.

- Error path — *Background refresh failure is logged and swallowed (R3)*: `block()`
  succeeds first, throws `RuntimeException("boom")` on next call; populate; wait for
  expiry; the next `invoke()` returns the stale value with no exception; the
  `ListAppender` captures a WARN with the throwable.

- Error path — *After a failed background refresh, a later call re-attempts (R3)*:
  `block()` throws once then returns a new value; populate; expire; `invoke()` returns
  stale (triggers failing refresh); wait past expiry again; `invoke()` returns stale
  once more while a new refresh is attempted; eventually the fresh value appears.

- Error path — *Cold-start failure still propagates (R6)*: `block()` throws on first
  call; `invoke()` throws — failures on cold start are surfaced, not swallowed, because
  there is no stale value to return.

- Integration — *No background work survives when the JVM exits is out of scope*, but
  verify that structured cancellation of the caller's scope does NOT cancel the
  background refresh: call `invoke()` inside a `launch` that is cancelled immediately
  after expiry triggers the refresh; wait; assert the value eventually refreshes.

**Verification:**
- `./gradlew test` passes the existing suite + all new scenarios on at least two
  consecutive runs (timing stability check).
- Kover line coverage for `CachedSuspending.kt` stays at or above the project threshold
  (91% instruction).

---

- [ ] U3. **Add CHANGES.md entry**

**Goal:** Record the fix in the changelog under an `Unreleased` section so the next
release picks it up.

**Requirements:** project convention (CLAUDE.md: CHANGES.md entries appended in
Unreleased, short, what-not-why, no YouTrack ID).

**Dependencies:** U1, U2

**Files:**
- Modify: `CHANGES.md`

**Approach:**
- Insert an `## Unreleased` section above `## 2026-03-31 / 1.4.0` if not already
  present; append a `### Fix` bullet: `fix CachedSuspending to actually refresh in the
  background without blocking callers, stampeding block(), or leaking exceptions`.

**Test scenarios:** Test expectation: none — documentation-only change.

**Verification:** CHANGES.md renders correctly and follows the existing section style.

---

## System-Wide Impact

- **Interaction graph:** The only consumers are direct callers of `invoke()`. No
  callbacks, filters, or middleware are affected. Downstream `op-api` call sites are
  source-compatible.
- **Error propagation:** Background-refresh failures no longer escape the cache —
  intentional, per R3. Cold-start failures still surface.
- **State lifecycle risks:** The internal `CoroutineScope` is never cancelled. Safe for
  application-singleton use (the documented use case); would leak if someone
  constructed a `CachedSuspending` per request. Considered acceptable — no current
  consumer does this (the `@RequestScope` case is read-mostly within a single request
  lifetime and ends when the scope ends).
- **API surface parity:** Constructor signature and `operator fun invoke()` signature
  are unchanged. Source-compatible and binary-compatible (class shape changes — `state`
  and `expires` fields are replaced — but they were `private`, so no external referent).
- **Integration coverage:** U2's single-flight and background-refresh scenarios exercise
  multi-coroutine interactions that unit-level mocks could not express; timing-based
  `delay`/poll scenarios substitute for a real TTL clock.
- **Unchanged invariants:** The documented contract ("always returns stale data and
  renews in the background") is now genuinely enforced rather than documented-but-
  broken — this is a behavioral fix, not a behavioral change from the consumer's
  perspective under correct usage.

---

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Timing-based tests flaky on slow CI | Use generous tolerances and active polling (`awaitEventually`) instead of fixed `delay` where possible. Verify two consecutive local runs pass before pushing. |
| Long-lived internal scope leaks in an unanticipated per-instance construction pattern | Accept — documented call sites are all singletons. If a future consumer needs per-request lifecycle, we add a `close()` then. |
| `kotlin-logging` bump from `testImplementation` to `implementation` adds a runtime dep for consumers | Dep is tiny (~60KB) and SLF4J-backed; every known consumer already pulls it in transitively via other Lovely libs. |
| `AtomicBoolean` + `@Volatile` snapshot + `Mutex` is three synchronization primitives in one class — reviewer complexity | Scope is small (≈50 lines) and each primitive has a single, named job (snapshot publishing, single-flight gate, cold-start serialization). Comment the why on each if a reviewer flags it. |

---

## Sources & References

- Origin issue: [OP-15224 — CachedSuspending does not refresh in the background](https://lovely.myjetbrains.com/youtrack/issue/OP-15224)
- File being fixed: `src/main/kotlin/ls/coroutines/CachedSuspending.kt`
- Existing test: `src/test/kotlin/ls/coroutines/CachedSuspendingTest.kt`
- Logback ListAppender idiom: `src/test/kotlin/ls/http/RequestIdFilterTest.kt`
- Known downstream call sites (in `op-api`, for regression testing after bump):
  - `events/proc/.../AirshipWatchedEventStream.kt:79`
  - `swisscom/.../SwisscomPaymentClient.kt:32`
  - `events/proc/.../AirshipApiClient.kt:41`
  - `model/.../RequestBasedProfileInfo.kt:76`
