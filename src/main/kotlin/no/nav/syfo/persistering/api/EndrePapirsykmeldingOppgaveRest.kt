package no.nav.syfo.persistering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.controllers.SendPapirsykmeldingController
import no.nav.syfo.log
import no.nav.syfo.metrics.SYKMELDING_KORRIGERT_COUNTER
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.sykmelder.exception.SykmelderNotFoundException
import no.nav.syfo.sykmelder.exception.UnauthorizedException
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import java.util.UUID

fun Route.endreSykmelding(
    sendPapirsykmeldingController: SendPapirsykmeldingController
) {
    route("/api/v1") {
        post("/oppgave/{oppgaveid}/endre") {
            val oppgaveId = call.parameters["oppgaveid"]?.toIntOrNull()

            log.info("Mottok kall til POST /api/v1/oppgave/$oppgaveId/endre")

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val callId = UUID.randomUUID().toString()
            val navEnhet = call.request.headers["X-Nav-Enhet"]

            val smRegistreringManuell: SmRegistreringManuell = call.receive()

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
                    try {
                        val httpServiceResponse = sendPapirsykmeldingController.sendPapirsykmelding(
                            smRegistreringManuell,
                            accessToken,
                            callId,
                            oppgaveId,
                            navEnhet,
                            isUpdate = true
                        )

                        when {
                            httpServiceResponse.payload != null -> {
                                SYKMELDING_KORRIGERT_COUNTER.inc()
                                call.respond(httpServiceResponse.httpStatusCode, httpServiceResponse.payload)
                            }
                            else -> {
                                call.respond(httpServiceResponse.httpStatusCode)
                            }
                        }
                    } catch (e: SykmelderNotFoundException) {
                        log.warn("Caught SykmelderNotFoundException", e)
                        call.respond(HttpStatusCode.NotFound, "Kunne ikke hente behandler")
                    } catch (e: UnauthorizedException) {
                        log.warn("Caught UnauthorizedException", e)
                        call.respond(
                            HttpStatusCode.Forbidden,
                            "Et eller flere av systemene rapporterer feil knyttet til tilgangskontroll"
                        )
                    } catch (e: ValidationException) {
                        log.warn("Caught ValidationException", e)
                        call.respond(HttpStatusCode.BadRequest, e.validationResult)
                    } catch (e: Exception) {
                        log.warn("Caught unexpected exception", e)
                        call.respond(HttpStatusCode.InternalServerError, "En ukjent feil har oppstått.")
                    }
                }
            }
        }
    }
}
