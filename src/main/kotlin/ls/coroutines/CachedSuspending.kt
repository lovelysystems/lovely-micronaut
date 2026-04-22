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

private val log = KotlinLogging.logger {}

/**
 * A suspending value cache that always returns stale data and renews in the background.
 *
 * On expiry, [invoke] returns the cached value immediately and schedules a refresh off
 * the caller's execution path. At most one refresh is in flight at any time; background
 * failures are logged and swallowed so callers keep receiving the stale value until a
 * later refresh succeeds.
 *
 * Intended for application-singleton use — the internal scope has no shutdown hook.
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
