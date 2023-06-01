package no.nav.syfo.persistering.api

import com.auth0.jwt.JWT
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.auditLogger.AuditLogger
import no.nav.syfo.auditlogg
import no.nav.syfo.controllers.SendTilGosysController
import no.nav.syfo.log
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.sikkerlogg
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import java.util.UUID

fun Route.sendOppgaveTilGosys(
    manuellOppgaveDAO: ManuellOppgaveDAO,
    sendTilGosysController: SendTilGosysController,
    authorizationService: AuthorizationService,
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
                        "Path er feilformattert: oppgaveid",
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
                            journalpostId = journalpostId,
                        )

                        if (authorizationService.hasAccess(accessToken, pasientFnr)) {
                            sendTilGosysController.sendOppgaveTilGosys(oppgaveId, sykmeldingId, accessToken, loggingMeta)

                            sikkerlogg.info(
                                "Veileder har ikkje tilgang navEmail:" +
                                    "${JWT.decode(accessToken).claims["preferred_username"]!!.asString()}, {}",
                                StructuredArguments.keyValue("oppgaveId", oppgaveId),
                            )

                            auditlogg.info(
                                AuditLogger().createcCefMessage(
                                    fnr = null,
                                    accessToken = accessToken,
                                    operation = AuditLogger.Operation.WRITE,
                                    requestPath = "/api/v1/oppgave/$oppgaveId/tilgosys",
                                    permit = AuditLogger.Permit.PERMIT,
                                ),
                            )

                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            log.warn(
                                "Veileder har ikke tilgang, {}",
                                StructuredArguments.keyValue("oppgaveId", oppgaveId),
                            )
                            auditlogg.info(
                                AuditLogger().createcCefMessage(
                                    fnr = null,
                                    accessToken = accessToken,
                                    operation = AuditLogger.Operation.WRITE,
                                    requestPath = "/api/v1/oppgave/$oppgaveId/tilgosys",
                                    permit = AuditLogger.Permit.DENY,
                                ),
                            )

                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                }
            }
        }
    }
}
