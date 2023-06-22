package no.nav.syfo.aksessering.api

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.syfo.controllers.FerdigstiltSykmeldingController
import no.nav.syfo.log
import no.nav.syfo.util.getAccessTokenFromAuthHeader

fun Route.hentFerdigstiltSykmelding(
    ferdigstiltSykmeldingController: FerdigstiltSykmeldingController,
) {
    route("/api/v1") {
        get("/sykmelding/{sykmeldingId}/ferdigstilt") {
            val sykmeldingId = call.parameters["sykmeldingId"]

            log.info("Mottok kall til GET /api/v1/sykmelding/$sykmeldingId/ferdigstilt")

            val accessToken = getAccessTokenFromAuthHeader(call.request)

            val httpServiceResponse =
                ferdigstiltSykmeldingController.hentFerdigstiltOppgave(accessToken, sykmeldingId)

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
