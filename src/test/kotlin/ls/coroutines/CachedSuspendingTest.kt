package ls.coroutines

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.seconds

class CachedSuspendingTest : ShouldSpec({

    val logger = LoggerFactory.getLogger("ls.coroutines") as Logger
    val appender = ListAppender<ILoggingEvent>()
    val initialLevel = logger.level

    beforeSpec {
        logger.level = Level.WARN
        appender.start()
        logger.addAppender(appender)
    }

    afterSpec {
        logger.detachAppender(appender)
        logger.level = initialLevel
    }

    afterTest {
        appender.list.clear()
    }

    context("a CachedSuspending with zero maxAge") {

        var called = 0
        val cs = CachedSuspending(Duration.ZERO) { ++called }

        should("return the stale result") {
            val first = cs()
            val second = cs()
            first shouldBe 1
            second shouldBe first

            delay(10)
            awaitValue(2.seconds) { cs() == 2 }
        }

    }

    context("a CachedSuspending with non zero maxAge") {

        var called = 0
        val cs = CachedSuspending(Duration.ofMillis(300)) { ++called }

        should("return the cached result") {
            cs() shouldBe 1
            delay(100)
            cs() shouldBe 1 // not expired
            cs() shouldBe 1 // not expired
            delay(210)
            cs() shouldBe 1 // expired, returned stale
            awaitValue(2.seconds) { cs() == 2 } // background refresh produces the new value
        }

    }

    context("background refresh contract") {

        should("return stale value immediately on expiry while refresh runs in background") {
            val calls = AtomicInteger(0)
            val firstDone = CompletableDeferred<Unit>()
            val releaseSecond = CompletableDeferred<Unit>()
            val cs = CachedSuspending(Duration.ofMillis(50)) {
                val n = calls.incrementAndGet()
                if (n == 1) {
                    firstDone.complete(Unit)
                } else {
                    releaseSecond.await()
                }
                n
            }

            cs() shouldBe 1
            firstDone.await()
            delay(80) // past TTL

            val elapsed = measureTimeMillis { cs() shouldBe 1 } // stale while bg runs
            elapsed shouldBeLessThan 200L

            releaseSecond.complete(Unit)
            awaitValue(2.seconds) { cs() == 2 }
        }

        should("run at most one refresh at a time when many callers hit an expired value") {
            val calls = AtomicInteger(0)
            val release = CompletableDeferred<Unit>()
            val cs = CachedSuspending(Duration.ofMillis(50)) {
                val n = calls.incrementAndGet()
                if (n > 1) release.await() // keep the second block in-flight
                n
            }

            cs() shouldBe 1
            delay(80) // expired
            calls.get() shouldBe 1

            // Fire a burst of concurrent invokes; only one should launch a new refresh.
            coroutineScope {
                (1..20).map { async(Dispatchers.Default) { cs() } }.awaitAll()
            }

            // Give the single scheduled bg refresh a chance to reach block()
            awaitValue(2.seconds) { calls.get() == 2 }
            release.complete(Unit)
            awaitValue(2.seconds) { cs() == 2 }
            calls.get() shouldBe 2
        }

        should("share a single block() invocation across concurrent cold-start callers") {
            val calls = AtomicInteger(0)
            val cs = CachedSuspending(Duration.ofMillis(100)) {
                calls.incrementAndGet()
                delay(50)
                42
            }

            val results = coroutineScope {
                (1..10).map { async(Dispatchers.Default) { cs() } }.awaitAll()
            }

            calls.get() shouldBe 1
            results shouldBe List(10) { 42 }
        }

        should("catch and log background refresh failures, keep returning the stale value") {
            val calls = AtomicInteger(0)
            val cs = CachedSuspending(Duration.ofMillis(50)) {
                val n = calls.incrementAndGet()
                if (n >= 2) error("boom-$n")
                "fresh"
            }

            cs() shouldBe "fresh"
            delay(80)

            // Trigger bg refresh that will throw inside the internal scope.
            cs() shouldBe "fresh"
            awaitValue(2.seconds) {
                appender.list.any { it.level == Level.WARN && it.throwableProxy?.message == "boom-2" }
            }

            // Stale value is still served; no exception propagates.
            cs() shouldBe "fresh"
        }

        should("re-attempt a refresh on a subsequent expiry after a prior bg failure") {
            val calls = AtomicInteger(0)
            val cs = CachedSuspending(Duration.ofMillis(50)) {
                when (calls.incrementAndGet()) {
                    1 -> "v1"
                    2 -> error("transient")
                    else -> "v3"
                }
            }

            cs() shouldBe "v1"
            delay(80)
            cs() shouldBe "v1" // triggers failing refresh
            awaitValue(2.seconds) {
                appender.list.any { it.level == Level.WARN && it.throwableProxy?.message == "transient" }
            }

            // Sanity: expires was not advanced by the failure, so the next call
            // observes an expired snapshot again and schedules another refresh.
            cs() shouldBe "v1"
            awaitValue(2.seconds) { cs() == "v3" }
        }

        should("propagate exceptions from cold-start failures") {
            val cs = CachedSuspending(Duration.ofMillis(50)) {
                error("cold-fail")
            }

            val ex = runCatching { cs() }.exceptionOrNull()
            (ex?.message ?: "") shouldBe "cold-fail"
        }

        should("survive cancellation of the caller that triggered the background refresh") {
            val calls = AtomicInteger(0)
            val secondStarted = CompletableDeferred<Unit>()
            val releaseSecond = CompletableDeferred<Unit>()
            val cs = CachedSuspending(Duration.ofMillis(50)) {
                val n = calls.incrementAndGet()
                if (n >= 2) {
                    secondStarted.complete(Unit)
                    releaseSecond.await()
                }
                n
            }

            cs() shouldBe 1
            delay(80)

            val triggeringJob = Job()
            withContext(triggeringJob) {
                cs() shouldBe 1 // triggers bg refresh from this scope
            }
            // Ensure the refresh actually started before we cancel.
            secondStarted.await()
            triggeringJob.cancel()

            // Background refresh is owned by the cache's own scope, so it completes
            // regardless of the triggering caller's cancellation.
            releaseSecond.complete(Unit)
            awaitValue(2.seconds) { cs() == 2 }
        }
    }

})

private suspend fun awaitValue(
    timeout: kotlin.time.Duration,
    pollIntervalMillis: Long = 10,
    predicate: suspend () -> Boolean,
) {
    val deadline = System.nanoTime() + timeout.inWholeNanoseconds
    while (System.nanoTime() < deadline) {
        if (predicate()) return
        delay(pollIntervalMillis)
    }
    // One last attempt so the failure message is produced by the caller's shouldBe, not a timeout.
    predicate() shouldBe true
}
