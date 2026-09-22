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

    /**
     * Client-controlled and bound for a log line, so the shape is matched whole or dropped.
     *
     * The cookie may carry more than the id: the ingress appends an ad click id after an `&`.
     * Only the part before the first separator is a visitor id, and matching the whole value
     * would drop the id for every visitor who ever arrived on an ad.
     */
    private fun HttpRequest<*>.visitorId(): String =
        cookies.findCookie(visitorCookie)
            .map { it.value.substringBefore(SEPARATOR) }
            .filter { VISITOR_ID_REGEX.matches(it) }
            .orElse(ABSENT)

    companion object {
        const val ABSENT = "-"

        /**
         * The two mint formats, either followed by unix seconds and milliseconds: the ingress uses
         * nginx's 32-hex request id, and Cloudflare a dashed uuid for the pages it answers without
         * the origin. An alternation rather than a widened character class, because this guards a
         * log line against a crafted value and both shapes stay exact.
         */
        private val VISITOR_ID_REGEX = Regex(
            "(?:[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})" +
                "\\.[0-9]{10}\\.[0-9]{3}"
        )

        /** What the ingress puts between the visitor id and anything it appends to it. */
        private const val SEPARATOR = '&'
    }
}
