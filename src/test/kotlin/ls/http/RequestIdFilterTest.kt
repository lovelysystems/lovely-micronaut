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

/** What Cloudflare mints for the pages it answers without the origin: a dashed uuid, not a
 *  32-hex request id. Taken verbatim from a real response. */
private const val VALID_EDGE_VISITOR_ID = "b3bbabe2-0a8c-41a5-a020-cf1174c714fe.1790068944.471"

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

    "an appended click id is not part of the visitor id" {

        val request: HttpRequest<*> = HttpRequest.create<Any>(HttpMethod.GET, "/hello")
            .cookie(Cookie.of("op_visitor", "$VALID_VISITOR_ID&g.EAIaIQobChMI5O6E0ZHolgMV"))
        httpClient.toBlocking().exchange(request, String::class.java)

        memoryAppender.list[0].mdcPropertyMap["visitorId"] shouldBe VALID_VISITOR_ID
    }

    "an edge-minted visitor cookie is stored in the MDC" {

        val request: HttpRequest<*> = HttpRequest.create<Any>(HttpMethod.GET, "/hello")
            .cookie(Cookie.of("op_visitor", VALID_EDGE_VISITOR_ID))
        httpClient.toBlocking().exchange(request, String::class.java)

        memoryAppender.list[0].mdcPropertyMap["visitorId"] shouldBe VALID_EDGE_VISITOR_ID
    }

    "an edge-minted id carrying a click id keeps only the id" {

        val request: HttpRequest<*> = HttpRequest.create<Any>(HttpMethod.GET, "/hello")
            .cookie(Cookie.of("op_visitor", "$VALID_EDGE_VISITOR_ID&g.EAIaIQobChMI5O6E0ZHolgMV"))
        httpClient.toBlocking().exchange(request, String::class.java)

        memoryAppender.list[0].mdcPropertyMap["visitorId"] shouldBe VALID_EDGE_VISITOR_ID
    }

    "a missing visitor cookie logs the absent marker" {

        val request: HttpRequest<*> = HttpRequest.create<Any>(HttpMethod.GET, "/hello")
        httpClient.toBlocking().exchange(request, String::class.java)

        memoryAppender.list[0].mdcPropertyMap["visitorId"] shouldBe "-"
    }

    // Anchored, so nothing survives being appended to a valid id except what follows the
    // separator. A line-forging value is absent because netty's validateHeader rejects a newline
    // before the request is sent.
    listOf(
        "not-a-visitor-id",
        VALID_VISITOR_ID.dropLast(1),
        VALID_VISITOR_ID + "extra",
        "&$VALID_VISITOR_ID",
        "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ.1789393893.921",
        VALID_VISITOR_ID.uppercase(),
        VALID_VISITOR_ID.replace(".", "-"),
        VALID_EDGE_VISITOR_ID.uppercase(),
        VALID_EDGE_VISITOR_ID.replaceFirst("-", ""),
        VALID_EDGE_VISITOR_ID.dropLast(1),
    ).forEachIndexed { index, bad ->
        // Indexed: several share a 24-char prefix and kotest needs distinct names.
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
