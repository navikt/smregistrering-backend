package no.nav.syfo.aksessering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.controllers.SendTilGosysController
import no.nav.syfo.log
import no.nav.syfo.model.PapirManuellOppgave
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.exception.SafForbiddenException
import no.nav.syfo.saf.exception.SafNotFoundException
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import no.nav.syfo.util.logNAVIdentTokenToSecureLogsWhenNoAccess

fun Route.hentPapirSykmeldingManuellOppgave(
    manuellOppgaveDAO: ManuellOppgaveDAO,
    safDokumentClient: SafDokumentClient,
    sendTilGosysController: SendTilGosysController,
    authorizationService: AuthorizationService
) {
    route("/api/v1") {
        get("/oppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]?.toIntOrNull()

            log.info("Mottok kall til GET /api/v1/oppgave/$oppgaveId")

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val manuellOppgaveDTOList = oppgaveId?.let {
                manuellOppgaveDAO.hentManuellOppgaver(it)
            } ?: emptyList()
            when {
                accessToken == null -> {
                    log.info("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.Unauthorized)
                }
                oppgaveId == null -> {
                    log.info("Ugyldig path parameter: oppgaveid")
                    call.respond(HttpStatusCode.BadRequest)
                }
                manuellOppgaveDTOList.isEmpty() -> {
                    log.info(
                        "Fant ingen uløste manuelloppgaver med oppgaveid {}",
                        StructuredArguments.keyValue("oppgaveId", oppgaveId)
                    )
                    call.respond(
                        HttpStatusCode.NotFound,
                        "Fant ingen uløste manuelle oppgaver med oppgaveid $oppgaveId"
                    )
                }
                else -> {
                    log.info(
                        "Henter ut oppgave med {}",
                        StructuredArguments.keyValue("oppgaveId", oppgaveId)
                    )

                    if (!manuellOppgaveDTOList.firstOrNull()?.fnr.isNullOrEmpty()) {
                        val fnr = manuellOppgaveDTOList.first().fnr!!

                        if (authorizationService.hasAccess(accessToken, fnr)) {

                            try {
                                val pdfPapirSykmelding = safDokumentClient.hentDokument(
                                    journalpostId = manuellOppgaveDTOList.first().journalpostId,
                                    dokumentInfoId = manuellOppgaveDTOList.first().dokumentInfoId ?: "",
                                    msgId = manuellOppgaveDTOList.first().sykmeldingId,
                                    accessToken = accessToken,
                                    sykmeldingId = manuellOppgaveDTOList.first().sykmeldingId
                                )
                                if (pdfPapirSykmelding == null) {
                                    call.respond(HttpStatusCode.InternalServerError)
                                } else {
                                    val papirManuellOppgave = PapirManuellOppgave(
                                        fnr = manuellOppgaveDTOList.first().fnr,
                                        sykmeldingId = manuellOppgaveDTOList.first().sykmeldingId,
                                        oppgaveid = oppgaveId,
                                        pdfPapirSykmelding = pdfPapirSykmelding,
                                        papirSmRegistering = manuellOppgaveDTOList.first().papirSmRegistering
                                    )

                                    call.respond(papirManuellOppgave)
                                }
                            } catch (safForbiddenException: SafForbiddenException) {
                                call.respond(HttpStatusCode.Forbidden, "Du har ikke tilgang til dokumentet i SAF")
                            } catch (safNotFoundException: SafNotFoundException) {

                                val sykmeldingId = manuellOppgaveDTOList.first().sykmeldingId
                                val journalpostId = manuellOppgaveDTOList.first().journalpostId
                                val dokumentInfoId = manuellOppgaveDTOList.first().dokumentInfoId

                                val loggingMeta = LoggingMeta(
                                    mottakId = sykmeldingId,
                                    dokumentInfoId = dokumentInfoId,
                                    msgId = sykmeldingId,
                                    sykmeldingId = sykmeldingId,
                                    journalpostId = journalpostId
                                )

                                sendTilGosysController.sendOppgaveTilGosys(oppgaveId, sykmeldingId, accessToken, loggingMeta)

                                call.respond(HttpStatusCode.Gone, "SENT_TO_GOSYS")
                            }
                        } else {
                            log.warn(
                                "Veileder har ikkje tilgang, {}",
                                StructuredArguments.keyValue("oppgaveId", oppgaveId)
                            )
                            logNAVIdentTokenToSecureLogsWhenNoAccess(accessToken)
                            call.respond(HttpStatusCode.Forbidden, "Veileder har ikke tilgang til oppgaven")
                        }
                    }
                }
            }
        }
    }
}
