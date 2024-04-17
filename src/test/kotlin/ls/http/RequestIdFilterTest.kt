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
import ls.http.RequestIdFilter
import org.slf4j.MDC
import java.util.*
import java.util.stream.Stream

val elements = mutableListOf<PropagatedContextElement>()

val testMutablePropagatedContext = object : MutablePropagatedContext {

    override fun add(@NonNull element: @NonNull PropagatedContextElement?): @NonNull MutablePropagatedContext? {
        elements.add(element!!)
        return this
    }

    override fun remove(element: PropagatedContextElement?): MutablePropagatedContext {
        return this
    }

    override fun replace(
        oldElement: PropagatedContextElement?,
        newElement: PropagatedContextElement?
    ): MutablePropagatedContext {
        return this
    }

    override fun getContext(): PropagatedContext {
        TODO("Not needed for this test")
    }
}

class RequestIdFilterTest : StringSpec({
    "rememberRequestId should propagate X-Request-ID header" {
        val requestIdFilter = RequestIdFilter()
        val request: HttpRequest<*> = HttpRequest.create<Any>(HttpMethod.GET, "/")
            .header("X-Request-ID", "testId")
        val context = testMutablePropagatedContext

        requestIdFilter.rememberRequestId(request, context)

        elements.size shouldBe 1
        val element = elements.first() as MdcPropagationContext
        element.state["requestId"] shouldBe "testId"
    }
})
