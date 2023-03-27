package ls.coroutines

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlin.system.measureTimeMillis

class CoFunctionsTest : StringSpec({

    val input = listOf(200L, 150L, 100L, 100L)

    suspend fun delayed(i: Long): Long {
        delay(i)
        return i
    }

    "concurrent flow should be mapped out of order in parallel" {
        val inFlow = input.asFlow()
        val elapsed = measureTimeMillis {
            inFlow.concurrentMap(5, ::delayed).toList() shouldBe input.sorted()
        }
        elapsed shouldBeLessThan input.sum()
    }

    "list should be mapped in order in parallel" {
        val elapsed = measureTimeMillis {
            input.mapInOrder(5, ::delayed).toList() shouldBe input
        }
        elapsed shouldBeLessThan input.sum()
    }

    "windowed should group by the specified key" {
        val total = input.size.toLong()

        val result = input
            .sorted()
            .asFlow()
            .windowed(total) { it }
            .map {
                "Key: ${it.first} | Batch: ${it.second.joinToString(separator = ",")}"
            }
            .toList()

        result shouldBe listOf(
            "Key: 100 | Batch: 100,100",
            "Key: 150 | Batch: 150",
            "Key: 200 | Batch: 200",
        )
    }

})
