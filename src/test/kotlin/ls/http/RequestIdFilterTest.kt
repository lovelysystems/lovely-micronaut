import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.propagation.slf4j.MdcPropagationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpHeaders
import io.mockk.*
import ls.http.RequestIdFilter
import org.slf4j.MDC
import java.util.*
import java.util.stream.Stream

class RequestIdFilterTest : StringSpec({
    "rememberRequestId should propagate X-Request-ID header" {
        val requestIdFilter = RequestIdFilter()
        val request: HttpRequest<*> = HttpRequest.create<Any>(HttpMethod.GET, "/")
            .header("X-Request-ID", "testId")

        val context = mockk<MutablePropagatedContext>()
        every { context.add(any<PropagatedContextElement>()) } returns context

        requestIdFilter.rememberRequestId(request, context)

        verify { context.add(match { (it as MdcPropagationContext).state["requestId"] == "testId" }) }
    }
})