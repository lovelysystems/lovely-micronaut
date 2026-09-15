package ls.http

import io.micronaut.context.annotation.Value
import io.micronaut.context.propagation.slf4j.MdcPropagationContext
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter

/**
 * Adds the `X-Request-ID` header to the MDC as `requestId`, and optionally a cookie as
 * `visitorId`. The visitor id is opt-in via `lovely.http.visitor-cookie`: it is a tracking
 * identifier and this library is shared. The cookie is minted on a response, so the first
 * request of a visit logs [ABSENT].
 */
@ServerFilter(Filter.MATCH_ALL_PATTERN)
class RequestIdFilter(
    @param:Value("\${lovely.http.visitor-cookie:}") private val visitorCookie: String,
) {
    @RequestFilter
    fun rememberRequestId(request: HttpRequest<*>, mutablePropagatedContext: MutablePropagatedContext) {
        val trackingId = request.headers.get("X-Request-ID")
        // trackingId or empty string
        val context = MdcPropagationContext(
            buildMap {
                put("requestId", trackingId.orEmpty())
                if (visitorCookie.isNotBlank()) put("visitorId", request.visitorId())
            }
        )
        mutablePropagatedContext.add(context)
    }

    /** Client-controlled and bound for a log line, so the shape is matched whole or dropped. */
    private fun HttpRequest<*>.visitorId(): String =
        cookies.findCookie(visitorCookie)
            .map { it.value }
            .filter { VISITOR_ID_REGEX.matches(it) }
            .orElse(ABSENT)

    companion object {
        const val ABSENT = "-"

        /** The ingress mint format: request id, unix seconds, milliseconds. */
        private val VISITOR_ID_REGEX = Regex("[0-9a-f]{32}\\.[0-9]{10}\\.[0-9]{3}")
    }
}
