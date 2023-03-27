package ls.coroutines

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import java.time.Duration

class CachedSuspendingTest : ShouldSpec({

    context("a CachedSuspending with zero maxAge") {

        var called = 0
        val cs = CachedSuspending(Duration.ZERO) { ++called }

        should("return the stale result") {
            val first = cs()
            val second = cs()
            first shouldBe 1
            second shouldBe first

            delay(10)
            val third = cs()
            third shouldBe 2
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
            delay(10)
            cs() shouldBe 2 // renewed
        }

    }

})