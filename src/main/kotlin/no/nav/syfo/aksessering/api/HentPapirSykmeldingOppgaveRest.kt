package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.model.PapirManuellOppgave
import no.nav.syfo.persistering.handleSendOppgaveTilGosys
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.exception.SafNotFoundException
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.getAccessTokenFromAuthHeader

fun Route.hentPapirSykmeldingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService,
    safDokumentClient: SafDokumentClient,
    oppgaveClient: OppgaveClient,
    authorizationService: AuthorizationService
) {
    route("/api/v1") {
        get("/oppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]?.toIntOrNull()

            log.info("Mottok kall til GET /api/v1/oppgave/$oppgaveId")

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            when {
                accessToken == null -> {
                    log.info("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.Unauthorized)
                }
                oppgaveId == null -> {
                    log.info("Ugyldig path parameter: oppgaveid")
                    call.respond(HttpStatusCode.BadRequest)
                }
                manuellOppgaveService.hentManuellOppgaver(oppgaveId).isEmpty() -> {
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

                    val manuellOppgaveDTOList = manuellOppgaveService.hentManuellOppgaver(oppgaveId)

                    if (!manuellOppgaveDTOList.firstOrNull()?.fnr.isNullOrEmpty()) {
                        val fnr = manuellOppgaveDTOList.first().fnr!!

                        if (authorizationService.hasAccess(accessToken, fnr)) {

                            try {

                                val pdfPapirSykmelding = safDokumentClient.hentDokument(
                                    journalpostId = manuellOppgaveDTOList.firstOrNull()?.journalpostId ?: "",
                                    dokumentInfoId = manuellOppgaveDTOList.firstOrNull()?.dokumentInfoId ?: "",
                                    msgId = manuellOppgaveDTOList.firstOrNull()?.sykmeldingId ?: "",
                                    accessToken = accessToken,
                                    oppgaveId = oppgaveId
                                )
                                if (pdfPapirSykmelding == null) {
                                    call.respond(HttpStatusCode.InternalServerError)
                                } else {
                                    val papirManuellOppgave = PapirManuellOppgave(
                                        fnr = manuellOppgaveDTOList.first().fnr,
                                        sykmeldingId = manuellOppgaveDTOList.first().sykmeldingId,
                                        oppgaveid = manuellOppgaveDTOList.first().oppgaveid,
                                        pdfPapirSykmelding = pdfPapirSykmelding,
                                        papirSmRegistering = manuellOppgaveDTOList.first().papirSmRegistering
                                    )

                                    call.respond(papirManuellOppgave)
                                }
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

                                handleSendOppgaveTilGosys(
                                    authorizationService = authorizationService,
                                    oppgaveClient = oppgaveClient,
                                    manuellOppgaveService = manuellOppgaveService,
                                    loggingMeta = loggingMeta,
                                    oppgaveId = oppgaveId,
                                    accessToken = accessToken
                                )

                                call.respond(HttpStatusCode.Gone, "SENT_TO_GOSYS")
                            }
                        } else {
                            log.warn(
                                "Veileder har ikkje tilgang, {}",
                                StructuredArguments.keyValue("oppgaveId", oppgaveId)
                            )
                            call.respond(HttpStatusCode.Unauthorized, "Veileder har ikke tilgang til oppgaven")
                        }
                    }
                }
            }
        }
    }
}
