package no.nav.syfo.persistering.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID
import no.nav.syfo.controllers.SendPapirsykmeldingController
import no.nav.syfo.log
import no.nav.syfo.model.SmRegistreringManuell
import no.nav.syfo.objectMapper
import no.nav.syfo.sikkerlogg
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

                    when {
                        httpRespons.payload != null -> {
                            call.respond(httpRespons.httpStatusCode, httpRespons.payload)
                        }
                        else -> {
                            call.respond(httpRespons.httpStatusCode)
                        }
                    }
                }
            }
        }
        post("/oppgave/{oppgaveid}/send") {
            val oppgaveId = call.parameters["oppgaveid"]?.toIntOrNull()

            log.info("Mottok kall til POST /api/v1/oppgave/$oppgaveId/send")
            val accessToken = getAccessTokenFromAuthHeader(call.request)
            log.info("Hentet access token fra header for $oppgaveId")
            val callId = UUID.randomUUID().toString()
            log.info("Lagde random UUID for $oppgaveId")
            val navEnhet = call.request.headers["X-Nav-Enhet"]
            log.info("Hentet NAV-enhet ($navEnhet) for $oppgaveId")
            val contentType = call.request.contentType()
            val contentLength = call.request.contentLength()
            log.info("Content-type: $contentType length: $contentLength")

            val bodyAsString = call.receiveNullable<String?>()

            sikkerlogg.info("Body som string: $bodyAsString")

            val smRegistreringManuell: SmRegistreringManuell =
                objectMapper.readValue(bodyAsString!!)

            log.info("Hentet body for $oppgaveId")

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
                        log.info("Attempting to send papirsykmelding for oppgave $oppgaveId")
                        val httpServiceResponse =
                            sendPapirsykmeldingController.sendPapirsykmelding(
                                smRegistreringManuell = smRegistreringManuell,
                                accessToken = accessToken,
                                callId = callId,
                                oppgaveId = oppgaveId,
                                navEnhet = navEnhet,
                                requestPath = "/api/v1/oppgave/$oppgaveId/send",
                            )
                        log.info("Successfully sent papirsykmelding for oppgave $oppgaveId")

                        when {
                            httpServiceResponse.payload != null -> {
                                call.respond(
                                    httpServiceResponse.httpStatusCode,
                                    httpServiceResponse.payload
                                )
                            }
                            else -> {
                                call.respond(httpServiceResponse.httpStatusCode)
                            }
                        }
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
