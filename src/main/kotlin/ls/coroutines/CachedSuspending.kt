package ls.coroutines

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/**
 * Returns a callable which cashes the given suspending [block].
 * The [block] is executed with an exclusive lock when a refresh is required, so no concurrent calls will never happen.
 *
 * This implementation always returns stale data and renews in the background.
 */
class CachedSuspending<T : Any>(private val maxAge: Duration, private val block: suspend () -> T) {
    private var state: T? = null
    private val mutex = Mutex()
    private var expires: Instant = Instant.EPOCH

    suspend operator fun invoke(): T {
        val currentState = state
        return if (currentState == null) {
            renew()
        } else {
            if (expires.isBefore(Instant.now())) {
                coroutineScope {
                    launch { renew() }
                }
            }
            currentState
        }
    }

    /**
     * renews the state by calling [block]
     */
    private suspend fun renew(): T = mutex.withLock {
        val res = block()
        expires = Instant.now() + maxAge
        state = res
        res
    }
}