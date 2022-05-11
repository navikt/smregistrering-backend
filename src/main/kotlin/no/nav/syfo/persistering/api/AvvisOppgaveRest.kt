package no.nav.syfo.persistering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveOrNull
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.controllers.AvvisPapirsykmeldingController
import no.nav.syfo.log
import no.nav.syfo.model.AvvisSykmeldingRequest
import no.nav.syfo.util.getAccessTokenFromAuthHeader

fun Route.avvisOppgave(
    avvisPapirsykmeldingController: AvvisPapirsykmeldingController,
) {
    route("/api/v1") {
        post("oppgave/{oppgaveId}/avvis") {
            val oppgaveId = call.parameters["oppgaveId"]?.toIntOrNull()

            log.info("Mottok kall til POST /api/v1/oppgave/$oppgaveId/avvis")

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val navEnhet = call.request.headers["X-Nav-Enhet"]

            val avvisSykmeldingRequest: AvvisSykmeldingRequest? = call.receiveOrNull()

            when {
                oppgaveId == null -> {
                    log.error("Path parameter mangler eller er feil formattert: oppgaveid")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Path parameter mangler eller er feil formattert: oppgaveid"
                    )
                }
                accessToken == null -> {
                    log.error("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.Unauthorized, "Mangler JWT Bearer token i HTTP header")
                }
                navEnhet == null -> {
                    log.error("Mangler X-Nav-Enhet i http header")
                    call.respond(HttpStatusCode.BadRequest, "Mangler X-Nav-Enhet i HTTP header")
                }
                else -> {

                    val httpServiceResponse = avvisPapirsykmeldingController.avvisPapirsykmelding(
                        oppgaveId,
                        accessToken,
                        navEnhet,
                        avvisSykmeldingRequest?.reason
                    )

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
    }
}
