package ls.http

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.cookie.Cookie
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import org.slf4j.LoggerFactory

/**
 * Visitor logging is opt-in. Without `lovely.http.visitor-cookie` no key is added at all, even
 * when the request carries a perfectly good cookie -- a shared library must not put a tracking
 * identifier into a service's logs just because it upgraded.
 */
@MicronautTest(transactional = false)
class RequestIdFilterDefaultTest(@Client("/") httpClient: HttpClient) : FreeSpec({
    val memoryAppender = ListAppender<ILoggingEvent>()
    val logger = LoggerFactory.getLogger("ls") as Logger
    val initialLevel = logger.level

    beforeSpec {
        logger.level = Level.INFO
        logger.addAppender(memoryAppender)
        memoryAppender.start()
    }

    afterSpec { logger.level = initialLevel }
    afterTest { memoryAppender.list.clear() }

    "no visitorId key is added when no cookie name is configured" {

        val request: HttpRequest<*> = HttpRequest.create<Any>(HttpMethod.GET, "/hello")
            .cookie(Cookie.of("op_visitor", "9b06fc462d83204abd16f53ee7f22882.1789393893.921"))
        httpClient.toBlocking().exchange(request, String::class.java)

        memoryAppender.list.size shouldBe 1
        memoryAppender.list[0].mdcPropertyMap.containsKey("visitorId") shouldBe false
        memoryAppender.list[0].mdcPropertyMap["requestId"] shouldBe ""
    }
})
