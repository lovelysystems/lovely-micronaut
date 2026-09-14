package ls.http

import io.micronaut.context.annotation.Value
import io.micronaut.context.propagation.slf4j.MdcPropagationContext
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter

/**
 * Filter that adds the request id and the visitor id to the MDC propagation context, so every log
 * line written while serving the request can be joined to the ingress access log.
 *
 * The request id is taken from the `X-Request-ID` header. The visitor id is taken from a cookie
 * whose name is configurable, because this library is shared and the cookie is not: set
 * `lovely.http.visitor-cookie` to the name the ingress issues.
 */
@ServerFilter(Filter.MATCH_ALL_PATTERN)
class RequestIdFilter(
    @param:Value("\${lovely.http.visitor-cookie:op_visitor}") private val visitorCookie: String,
) {
    @RequestFilter
    fun rememberRequestId(request: HttpRequest<*>, mutablePropagatedContext: MutablePropagatedContext) {
        val trackingId = request.headers.get("X-Request-ID")
        // trackingId or empty string
        val context = MdcPropagationContext(
            mapOf(
                "requestId" to trackingId.orEmpty(),
                "visitor" to request.visitorId(),
            )
        )
        mutablePropagatedContext.add(context)
    }

    /**
     * The visitor id, or [ABSENT] when the cookie is missing or does not match the shape the
     * ingress mints. A cookie is client-controlled and this value reaches a log line, so an
     * unrecognised one is dropped rather than logged: an anchored match bounds it to hex digits
     * and dots, which cannot break the line it is written on.
     */
    private fun HttpRequest<*>.visitorId(): String =
        cookies.findCookie(visitorCookie)
            .map { it.value }
            .filter { VISITOR_ID_REGEX.matches(it) }
            .orElse(ABSENT)

    companion object {
        /** Logged when no usable visitor id is on the request. */
        const val ABSENT = "-"

        /** Mirrors the ingress mint format: request id, unix seconds, milliseconds. */
        private val VISITOR_ID_REGEX = Regex("[0-9a-f]{32}\\.[0-9]{10}\\.[0-9]{3}")
    }
}
