package ls.coroutines

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Maps all items of an [Iterable] with a given [concurrencyLevel] in parallel.
 * In contrast to [concurrentMap] the item ordering is kept.
 */
inline fun <T, R> Iterable<T>.mapInOrder(concurrencyLevel: Int, crossinline block: suspend (T) -> R): Flow<R> {
    val semaphore = Semaphore(concurrencyLevel)
    return channelFlow {
        forEach {
            semaphore.acquire()
            send(async { block(it) })
        }
    }.map { it.await() }.onEach { semaphore.release() }
}

/**
 * Maps all items of a [Flow] with a given [concurrencyLevel] in parallel without preserving order.
 */
@OptIn(FlowPreview::class)
fun <T, R> Flow<T>.concurrentMap(
    concurrencyLevel: Int,
    transform: suspend (T) -> R,
): Flow<R> {
    return flatMapMerge(concurrencyLevel) { value ->
        flow { emit(transform(value)) }
    }
}

/**
 * Groups items into batches using the provided [keySelector]. [total] must be the total number of items produced by the
 * flow. Flow items must be sorted by the [keySelector]!
 */
fun <K : Comparable<K>, T> Flow<T>.windowed(total: Long, keySelector: (T) -> K): Flow<Pair<K, List<T>>> =
    flow {
        var totalCollected = 0L
        val window = mutableListOf<T>()
        var windowEnd: K? = null

        collect {el ->
            val key = keySelector(el)

            if (windowEnd == null) {
                window += el
                windowEnd = key
            } else {
                if (key > windowEnd!!) {
                    emit(windowEnd!! to window)
                    windowEnd = key
                    window.clear()
                    window += el
                } else {
                    window += el
                }
            }

            totalCollected += 1
            if (totalCollected == total) emit(windowEnd!! to window)
        }
    }



/**
 * Retries the given block with the interval between executions until the block returns not null
 * or the timeout is exceeded (raises [TimeoutCancellationException])
 */
suspend fun <T> waitUntilNotNull(interval: Duration, timeout: Duration, block : suspend () -> T?) : T =
    withTimeout(timeout.toLong(DurationUnit.MILLISECONDS)) {
        var value = block()
        while (value==null) {
            delay(interval)
            value = block()
        }
        value
    }
