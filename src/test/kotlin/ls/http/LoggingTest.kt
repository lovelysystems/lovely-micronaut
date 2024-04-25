package ls.http

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import mu.KotlinLogging
import java.io.ByteArrayOutputStream
import java.io.PrintStream

@MicronautTest(transactional = false)
class LoggingTest(@Client("/") httpClient: HttpClient) : FreeSpec({

    val oldOut = System.out
    val mapper = ObjectMapper()
    val timestampRegex = """^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}[+-]\d{2}:\d{2}$""".toRegex()

    "captured logs" {
        // Create ByteArrayOutputStream to capture logs
        val baos = ByteArrayOutputStream()
        // Redirect System.out to the ByteArrayOutputStream
        System.setOut(PrintStream(baos))
        val request = HttpRequest.GET<String>("/log")
            .header("x-request-id", "my-request-id")
        try {
            httpClient.toBlocking().exchange(request, String::class.java)
        } catch (e: Exception) {
            // The request will log an INFO log and an ERROR log and fail
        }

        // Reset System.out to its original value
        System.setOut(oldOut)

        // Get logs from ByteArrayOutputStream and convert to string
        val logs = baos.toString()

        // The info log should contain the following fields
        val infoLines = logs.lines().filter { it.contains("INFO") }
        infoLines.size shouldBe 1
        val infoObject = mapper.readValue(infoLines.first(), Map::class.java)
        infoObject["level"] shouldBe "INFO"
        infoObject["level_value"] shouldBe 20000
        infoObject["logger"] shouldBe "ls.http.LoggingTestController"
        infoObject["message"] shouldBe "Hello World\nLine2"
        infoObject["requestId"] shouldBe "my-request-id"
        infoObject["thread"].toString() shouldStartWith "default-nioEventLoopGroup"
        infoObject["time"].toString() shouldMatch timestampRegex
        infoObject["version"] shouldBe "1"

        // The error log also contains a stack trace
        val errorLines = logs.lines().filter { it.contains("ERROR") }
        infoLines.size shouldBe 1
        val errorObject = mapper.readValue(errorLines.first(), Map::class.java)
        errorObject["level"] shouldBe "ERROR"
        errorObject["message"] shouldBe "Unexpected error occurred: Goodbye World"
        errorObject["stack_trace"].toString() shouldStartWith "java.lang.RuntimeException: Goodbye World\n\tat ls.http.LoggingTestController.log"
    }
})

@Controller(produces = [MediaType.TEXT_PLAIN])
class LoggingTestController {

    private val logger = KotlinLogging.logger { }

    @Get("/log")
    fun log(): String {
        logger.info { "Hello World\nLine2" }
        throw RuntimeException("Goodbye World")
    }
}

