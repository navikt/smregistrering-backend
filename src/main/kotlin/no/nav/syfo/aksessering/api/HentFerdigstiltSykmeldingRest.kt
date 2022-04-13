package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.controllers.FerdigstiltSykmeldingController
import no.nav.syfo.log
import no.nav.syfo.util.getAccessTokenFromAuthHeader

fun Route.hentFerdigstiltSykmelding(
    ferdigstiltSykmeldingController: FerdigstiltSykmeldingController
) {
    route("/api/v1") {
        get("/sykmelding/{sykmeldingId}/ferdigstilt") {
            val sykmeldingId = call.parameters["sykmeldingId"]

            log.info("Mottok kall til GET /api/v1/sykmelding/$sykmeldingId/ferdigstilt")

            val accessToken = getAccessTokenFromAuthHeader(call.request)

            val httpServiceResponse = ferdigstiltSykmeldingController.hentFerdigstiltOppgave(accessToken, sykmeldingId)

            when {
                httpServiceResponse.payload != null -> {
                    call.respond(httpServiceResponse.httpStatusCode, httpServiceResponse.payload)
                }
                else -> {
                    call.respond(httpServiceResponse.httpStatusCode)
                }
            }
        }
    }
}
