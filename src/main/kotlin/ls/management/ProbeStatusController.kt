package ls.management

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

/**
 * Needed for running the API behind a conventional Lovely RP which checks the `/probe_status` endpoint of the upstreams
 */
@Controller(produces = [MediaType.TEXT_PLAIN])
class ProbeStatusController {

    @Get("/probe_status")
    fun probeStatus() = OK

    companion object {
        const val OK = "OK"
    }
}
