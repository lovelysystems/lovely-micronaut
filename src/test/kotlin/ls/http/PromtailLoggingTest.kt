package ls.http

import ch.qos.logback.classic.spi.ILoggingEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith
import io.micronaut.context.annotation.Property
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import mu.KotlinLogging
import org.slf4j.MDC
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory

import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.OutputStreamAppender
import net.logstash.logback.encoder.LogstashEncoder

/**
 * A custom appender that captures logs in a list and formats them using the LogstashEncoder
 * used in logback-promtail.xml.
 */
class FormattedListAppender(rootLogger: Logger) : AppenderBase<ILoggingEvent>() {
    val logs = mutableListOf<String>()
    private val encoder: LogstashEncoder

    init {
        val stdoutAppender = rootLogger.getAppender("STDOUT") as? OutputStreamAppender<ILoggingEvent>
        encoder = (stdoutAppender?.encoder as? LogstashEncoder)!!
    }

    override fun append(eventObject: ILoggingEvent) {
        val byteArray = encoder.encode(eventObject)
        val formattedMessage = String(byteArray)
        logs.add(formattedMessage)
    }
}

@MicronautTest(transactional = false)
@Property(name = "logger.config", value = "logback-promtail.xml")
class PromtailLoggingTest(@Client("/") httpClient: HttpClient) : FreeSpec({

    val mapper = ObjectMapper()
    val timestampRegex = """^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}[+-]\d{2}:\d{2}$""".toRegex()

    val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    val listAppender = FormattedListAppender(rootLogger)

    beforeSpec {
        rootLogger.addAppender(listAppender)
        listAppender.start()
    }

    afterSpec {
        rootLogger.detachAppender(listAppender)
    }

    "captured logs" {
        try {
            httpClient.toBlocking().exchange("/log", String::class.java)
        } catch (e: Exception) {
            // The request will log an INFO log and an ERROR log and fail
        }

        val logs = listAppender.logs

        // The info log should contain the following fields
        val infoLines = logs.filter { it.contains("INFO") }
        infoLines.size shouldBe 1
        val infoObject = mapper.readValue<Map<String, String>>(infoLines.first())
        infoObject["level"] shouldBe "INFO"
        infoObject["level_value"] shouldBe "20000"
        infoObject["logger"] shouldBe "ls.http.PromtailLoggingTestController"
        infoObject["mdcTestKey"] shouldBe "some_value"
        infoObject["message"] shouldBe "Hello World\nLine2"
        infoObject["thread"].toString() shouldStartWith "default-nioEventLoopGroup"
        infoObject["time"].toString() shouldMatch timestampRegex
        infoObject["version"] shouldBe "1"

        // The error log also contains a stack trace
        val errorLines = logs.filter { it.contains("ERROR") }
        infoLines.size shouldBe 1
        val errorObject = mapper.readValue<Map<String, String>>(errorLines.first())
        errorObject["level"] shouldBe "ERROR"
        errorObject["message"] shouldBe "Unexpected error occurred: Goodbye World"
        errorObject["stack_trace"].toString() shouldStartWith "java.lang.RuntimeException: Goodbye World\n\tat ls.http.PromtailLoggingTestController.log"
    }
})

@Controller(produces = [MediaType.TEXT_PLAIN])
class PromtailLoggingTestController {

    private val logger = KotlinLogging.logger { }

    @Get("/log")
    fun log(): String {
        MDC.put("mdcTestKey", "some_value")
        logger.info { "Hello World\nLine2" }
        throw RuntimeException("Goodbye World")
    }
}

