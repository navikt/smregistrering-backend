package no.nav.syfo.persistering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.pipeline.PipelineContext
import java.util.UUID
import no.nav.syfo.controllers.HttpServiceResponse
import no.nav.syfo.controllers.SendPapirsykmeldingController
import no.nav.syfo.log
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.sykmelder.exception.SykmelderNotFoundException
import no.nav.syfo.sykmelder.exception.UnauthorizedException
import no.nav.syfo.util.getAccessTokenFromAuthHeader

fun Route.sendPapirSykmeldingManuellOppgave(
    sendPapirsykmeldingController: SendPapirsykmeldingController,
) {
    route("/api/v1") {
        post("/sykmelding/{sykmeldingId}") {
            val sykmeldingId = call.parameters["sykmeldingId"]
            log.info("sender sykmelding: $sykmeldingId")

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val callId = UUID.randomUUID().toString()
            val navEnhet = call.request.headers["X-Nav-Enhet"]

            val smRegistreringManuell: SmRegistreringManuell = call.receive()

            when {
                sykmeldingId == null -> {
                    log.error("Path parameter mangler eller er feil formattert: sykmeldingId")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Path parameter mangler eller er feil formattert: sykmeldingId",
                    )
                }
                accessToken == null -> {
                    log.error("Mangler JWT Bearer token i HTTP header")
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        "Mangler JWT Bearer token i HTTP header"
                    )
                }
                navEnhet == null -> {
                    log.error("Mangler X-Nav-Enhet i http header")
                    call.respond(HttpStatusCode.BadRequest, "Mangler X-Nav-Enhet i HTTP header")
                }
                else -> {
                    val httpRespons =
                        sendPapirsykmeldingController.sendPapirsykmelding(
                            smRegistreringManuell = smRegistreringManuell,
                            accessToken = accessToken,
                            callId = callId,
                            sykmeldingId = sykmeldingId,
                            navEnhet = navEnhet,
                            requestPath = "/api/v1/sykmelding/$sykmeldingId",
                        )

                    respond(httpRespons)
                }
            }
        }
        post("/oppgave/{oppgaveid}/send") {
            val oppgaveId = call.parameters["oppgaveid"]?.toIntOrNull()

            log.info("Mottok kall til POST /api/v1/oppgave/$oppgaveId/send")

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val callId = UUID.randomUUID().toString()
            val navEnhet = call.request.headers["X-Nav-Enhet"]

            val smRegistreringManuell: SmRegistreringManuell = call.receive()

            when {
                oppgaveId == null -> {
                    log.error("Path parameter mangler eller er feil formattert: oppgaveid")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Path parameter mangler eller er feil formattert: oppgaveid",
                    )
                }
                accessToken == null -> {
                    log.error("Mangler JWT Bearer token i HTTP header")
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        "Mangler JWT Bearer token i HTTP header"
                    )
                }
                navEnhet == null -> {
                    log.error("Mangler X-Nav-Enhet i http header")
                    call.respond(HttpStatusCode.BadRequest, "Mangler X-Nav-Enhet i HTTP header")
                }
                else -> {
                    try {
                        val httpServiceResponse =
                            sendPapirsykmeldingController.sendPapirsykmelding(
                                smRegistreringManuell = smRegistreringManuell,
                                accessToken = accessToken,
                                callId = callId,
                                oppgaveId = oppgaveId,
                                navEnhet = navEnhet,
                                requestPath = "/api/v1/oppgave/$oppgaveId/send",
                            )

                        respond(httpServiceResponse)
                    } catch (e: SykmelderNotFoundException) {
                        log.warn("Caught SykmelderNotFoundException", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            "Noe gikk galt ved uthenting av behandler"
                        )
                    } catch (e: UnauthorizedException) {
                        log.warn("Caught UnauthorizedException", e)
                        call.respond(
                            HttpStatusCode.Forbidden,
                            "Et eller flere av systemene rapporterer feil knyttet til tilgangskontroll",
                        )
                    } catch (e: ValidationException) {
                        log.warn("Caught ValidationException", e)
                        call.respond(HttpStatusCode.BadRequest, e.validationResult)
                    } catch (e: Exception) {
                        log.error("Caught unexpected exception", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            "En ukjent feil har oppstått."
                        )
                    }
                }
            }
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.respond(
    httpServiceResponse: HttpServiceResponse,
) {
    when {
        httpServiceResponse.payload != null -> {
            call.respond(httpServiceResponse.httpStatusCode, httpServiceResponse.payload)
        }
        else -> {
            call.respond(httpServiceResponse.httpStatusCode)
        }
    }
}
