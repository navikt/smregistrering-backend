package no.nav.syfo.sykmelder.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import java.util.UUID
import no.nav.syfo.log
import no.nav.syfo.sykmelder.service.SykmelderService
import no.nav.syfo.util.getAccessTokenFromAuthHeader

fun Route.sykmelderApi(
    sykmelderService: SykmelderService
) {
    route("/api/v1") {
        get("/sykmelder/{hprNummer}") {
            val hprNummer = call.parameters["hprNummer"]?.toIntOrNull()

            log.info("Mottok kall til GET /api/v1/sykmelder/$hprNummer")

            when {
                hprNummer == null -> {
                    log.info("Ugyldig path parameter: hprNummer")
                    call.respond(HttpStatusCode.BadRequest)
                }
                else -> {
                    val accessToken = getAccessTokenFromAuthHeader(call.request)!!
                    val callId = UUID.randomUUID().toString()
                    val sykmelder = sykmelderService.hentSykmelder(hprNummer.toString(), accessToken, callId)
                    call.respond(sykmelder)
                }
            }
        }
    }
}
