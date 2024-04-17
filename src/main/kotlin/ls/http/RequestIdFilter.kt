package ls.http

import io.micronaut.context.propagation.slf4j.MdcPropagationContext
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter

/**
 * Filter that adds the request id to the MDC propagation context.
 * The request id is taken from the `X-Request-ID` header.
 *
 * The request id will be included in all logback-promtail logs.
 *
 * The logback configuration can be found here:
 * - src/main/resources/logback-promtail.xml
 */
@ServerFilter(Filter.MATCH_ALL_PATTERN)
class RequestIdFilter {
    @RequestFilter
    fun rememberRequestId(request: HttpRequest<*>, mutablePropagatedContext: MutablePropagatedContext) {
        val trackingId = request.headers.get("X-Request-ID")
        // trackingId or empty string
        val context = MdcPropagationContext(mutableMapOf("requestId" to trackingId.orEmpty()))
        mutablePropagatedContext.add(context)
    }
}