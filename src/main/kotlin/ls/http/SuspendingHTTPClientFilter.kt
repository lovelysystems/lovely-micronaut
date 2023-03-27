package ls.http

import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import kotlinx.coroutines.reactor.flux
import org.reactivestreams.Publisher

/**
 * A [HttpClientFilter] which allows filtering requests with suspending functions.
 */
interface SuspendingHTTPClientFilter : HttpClientFilter {

    override fun doFilter(request: MutableHttpRequest<*>, chain: ClientFilterChain): Publisher<out HttpResponse<*>> {
        return flux {
            send(doFilter(request))
        }.flatMap { chain.proceed(it) }
    }

    suspend fun doFilter(request: MutableHttpRequest<*>): MutableHttpRequest<*>
}