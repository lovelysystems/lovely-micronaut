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
import io.micronaut.context.annotation.Property
import io.micronaut.http.MediaType
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import org.slf4j.LoggerFactory

private const val VALID_VISITOR_ID = "9b06fc462d83204abd16f53ee7f22882.1789393893.921"

@MicronautTest(transactional = false)
@Property(name = "lovely.http.visitor-cookie", value = "op_visitor")
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

    "the visitor cookie is stored in the MDC" {

        val request: HttpRequest<*> = HttpRequest.create<Any>(HttpMethod.GET, "/hello")
            .cookie(Cookie.of("op_visitor", VALID_VISITOR_ID))
        httpClient.toBlocking().exchange(request, String::class.java)

        memoryAppender.list[0].mdcPropertyMap["visitorId"] shouldBe VALID_VISITOR_ID
    }

    "a missing visitor cookie logs the absent marker" {

        val request: HttpRequest<*> = HttpRequest.create<Any>(HttpMethod.GET, "/hello")
        httpClient.toBlocking().exchange(request, String::class.java)

        memoryAppender.list[0].mdcPropertyMap["visitorId"] shouldBe "-"
    }

    // A cookie is client-controlled and this value lands in a log line, so anything that is not
    // the shape the ingress mints is dropped. `Regex.matches` matches the whole input rather than
    // searching it, so nothing can be appended to a valid id and survive.
    //
    // A line-forging value is not in this list because it cannot be sent: netty's
    // `NettyHttpHeaders.validateHeader` rejects a newline when the client sets the cookie header,
    // so the request never leaves. (`Cookie.of` itself accepts one -- the guard is the transport,
    // not the cookie factory.) The anchored match is the layer behind that, for whatever reaches
    // the filter by another route.
    listOf(
        "not-a-visitor-id",
        VALID_VISITOR_ID.dropLast(1),
        VALID_VISITOR_ID + "extra",
        "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ.1789393893.921",
        VALID_VISITOR_ID.uppercase(),
        VALID_VISITOR_ID.replace(".", "-"),
    ).forEachIndexed { index, bad ->
        // Indexed: several of these share a 24-char prefix, and kotest needs distinct test names.
        "a malformed visitor cookie logs the absent marker ($index)" {

            val request: HttpRequest<*> = HttpRequest.create<Any>(HttpMethod.GET, "/hello")
                .cookie(Cookie.of("op_visitor", bad))
            httpClient.toBlocking().exchange(request, String::class.java)

            memoryAppender.list[0].mdcPropertyMap["visitorId"] shouldBe "-"
        }
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
