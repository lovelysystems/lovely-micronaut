package ls.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import org.slf4j.LoggerFactory

@MicronautTest(transactional = false)
class RequestIdFilterTest(@Client("/") httpClient: HttpClient) : FreeSpec({
    val memoryAppender = ListAppender<ILoggingEvent>()
    val logger = LoggerFactory.getLogger("ls") as Logger
    val initialLevel = logger.level

    beforeSpec {
        logger.level = Level.INFO
        logger.addAppender(memoryAppender)
        memoryAppender.start()
    }

    afterSpec {
        logger.level = initialLevel
    }

    afterTest {
        memoryAppender.list.clear()
    }

    "rememberRequestId should propagate X-Request-ID header" {

        val request: HttpRequest<*> = HttpRequest.create<Any>(HttpMethod.GET, "/hello")
            .header("X-Request-ID", "testId")
        httpClient.toBlocking().exchange(request, String::class.java)

        memoryAppender.list.size shouldBe 1
        memoryAppender.list[0].message shouldBe "Hello World"
        // the requestId is stored in the MDC
        memoryAppender.list[0].mdcPropertyMap["requestId"] shouldBe "testId"
    }
}) {

    @Controller(produces = [MediaType.TEXT_PLAIN])
    class RequestIdFilterTestController {

        private val logger = KotlinLogging.logger { }

        @Get("/hello")
        fun log(): String {
            logger.info { "Hello World" }
            return "OK"
        }
    }
}
