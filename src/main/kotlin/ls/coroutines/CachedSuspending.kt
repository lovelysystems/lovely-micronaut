package ls.coroutines

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

/**
 * A suspending value cache that returns the cached value immediately and refreshes in the
 * background when expired.
 *
 * On expiry, [invoke] returns the cached value and schedules a refresh off the caller's
 * execution path. At most one refresh is in flight at any time; background failures are
 * logged and swallowed so callers keep receiving the stale value until a later refresh
 * succeeds.
 *
 * The first call suspends until [block] produces a value; concurrent first-callers share
 * that single invocation.
 *
 * Intended for application-singleton use — each instance owns an internal CoroutineScope
 * that lives for the lifetime of the instance and is never cancelled. Do not construct
 * per-request or per-call; that leaks a scope per instance.
 *
 * When caching a resource with its own server-side validity (OAuth tokens, signed URLs,
 * etc.), pick [maxAge] comfortably shorter than that validity window. Expired callers
 * receive the stale value until the background refresh completes, so [maxAge] equal to
 * the upstream expiration can surface an already-rejected value to the caller.
 */
class CachedSuspending<T : Any>(private val maxAge: Duration, private val block: suspend () -> T) {

    private data class Snapshot<T>(val value: T, val expires: Instant)

    @Volatile
    private var snapshot: Snapshot<T>? = null
    private val mutex = Mutex()
    private val refreshing = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend operator fun invoke(): T {
        val current = snapshot ?: return coldStart()
        if (current.expires.isBefore(Instant.now())) {
            triggerBackgroundRefresh()
        }
        return current.value
    }

    // Known limitation: if many callers arrive before the first successful populate and block()
    // persistently fails, they serialize on this mutex and each re-runs block() against the
    // failing backend. Acceptable for singleton usage where cold start is rarely contended;
    // revisit with a negative-caching window if herd behavior becomes a real problem.
    private suspend fun coldStart(): T = mutex.withLock {
        snapshot?.value ?: computeAndStore()
    }

    private fun triggerBackgroundRefresh() {
        if (!refreshing.compareAndSet(false, true)) return
        try {
            scope.launch {
                try {
                    mutex.withLock { computeAndStore() }
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    logger.warn(e) { "CachedSuspending background refresh failed" }
                } finally {
                    refreshing.set(false)
                }
            }
        } catch (e: Exception) {
            // Scheduling itself failed; keep the gate unstuck so a later expiry can retry.
            refreshing.set(false)
            logger.warn(e) { "CachedSuspending failed to schedule background refresh" }
        }
    }

    private suspend fun computeAndStore(): T {
        // Compute expiry before invoking block() so a pathological maxAge fails fast without
        // running side effects we would then have to discard.
        val expires = Instant.now() + maxAge
        val value = block()
        snapshot = Snapshot(value, expires)
        return value
    }
}
