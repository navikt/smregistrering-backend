package no.nav.syfo.persistering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.controllers.SendTilGosysController
import no.nav.syfo.log
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import no.nav.syfo.util.logNAVIdentTokenToSecureLogs
import java.util.UUID

fun Route.sendOppgaveTilGosys(
    manuellOppgaveDAO: ManuellOppgaveDAO,
    sendTilGosysController: SendTilGosysController,
    authorizationService: AuthorizationService
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

                    val manuellOppgaveDTOList = manuellOppgaveDAO.hentManuellOppgaver(oppgaveId)

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
                            journalpostId = journalpostId
                        )

                        if (authorizationService.hasAccess(accessToken, pasientFnr)) {
                            logNAVIdentTokenToSecureLogs(accessToken, true)

                            sendTilGosysController.sendOppgaveTilGosys(oppgaveId, sykmeldingId, accessToken, loggingMeta)

                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            log.warn(
                                "Veileder har ikke tilgang, {}",
                                StructuredArguments.keyValue("oppgaveId", oppgaveId)
                            )
                            logNAVIdentTokenToSecureLogs(accessToken, false)
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                }
            }
        }
    }
}
