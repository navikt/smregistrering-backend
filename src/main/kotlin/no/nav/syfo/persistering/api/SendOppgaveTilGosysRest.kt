package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.persistering.handleSendOppgaveTilGosys
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import java.util.UUID

fun Route.sendOppgaveTilGosys(
    manuellOppgaveService: ManuellOppgaveService,
    authorizationService: AuthorizationService,
    oppgaveClient: OppgaveClient
) {
    route("/api/v1") {
        post("oppgave/{oppgaveId}/tilgosys") {
            val oppgaveId = call.parameters["oppgaveId"]?.toIntOrNull()

            log.info("Mottok kall til POST /api/v1/oppgave/$oppgaveId/tilgosys")

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val callId = UUID.randomUUID().toString()

            when {
                oppgaveId == null -> {
                    log.error("Path parameter er feilformattert: oppgaveid")
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Path er feilformattert: oppgaveid"
                    )
                }
                accessToken.isNullOrEmpty() -> {
                    log.error("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.Unauthorized, "Mangler JWT Bearer token i HTTP header")
                }
                else -> {

                    val manuellOppgaveDTOList = manuellOppgaveService.hentManuellOppgaver(oppgaveId)

                    if (manuellOppgaveDTOList.isEmpty()) {
                        log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        val sykmeldingId = manuellOppgaveDTOList.first().sykmeldingId
                        val journalpostId = manuellOppgaveDTOList.first().journalpostId
                        val dokumentInfoId = manuellOppgaveDTOList.first().dokumentInfoId
                        val pasientFnr = manuellOppgaveDTOList.first().fnr!!

                        val loggingMeta = LoggingMeta(
                            mottakId = sykmeldingId,
                            dokumentInfoId = dokumentInfoId,
                            msgId = callId,
                            sykmeldingId = sykmeldingId,
                            journalpostId = journalpostId,
                            source = "api"
                        )

                        if (authorizationService.hasAccess(accessToken, pasientFnr)) {

                            handleSendOppgaveTilGosys(
                                authorizationService,
                                oppgaveClient,
                                manuellOppgaveService,
                                loggingMeta,
                                oppgaveId,
                                accessToken
                            )

                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            log.warn(
                                "Veileder har ikke tilgang, {}",
                                StructuredArguments.keyValue("oppgaveId", oppgaveId)
                            )
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                }
            }
        }
    }
}
