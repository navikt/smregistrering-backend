package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.syfo.SyfoTilgangsKontrollService
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.log
import no.nav.syfo.model.PapirManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader

@KtorExperimentalAPI
fun Route.hentPapirSykmeldingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService,
    safDokumentClient: SafDokumentClient,
    syfoTilgangsKontrollService: SyfoTilgangsKontrollService,
    cluster: String
) {
    route("/api/v1") {
        get("/hentPapirSykmeldingManuellOppgave") {
            log.info("Mottok kall til /api/v1/hentPapirSykmeldingManuellOppgave")
            val oppgaveId = call.request.queryParameters["oppgaveid"]?.toInt()
            val accessToken = getAccessTokenFromAuthHeader(call.request)

            when {
                accessToken == null -> {
                    log.info("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.BadRequest)
                }
                oppgaveId == null -> {
                    log.info("Mangler query parameters: oppgaveid")
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
                    val pdfPapirSykmelding = safDokumentClient.hentDokument(
                        journalpostId = manuellOppgaveDTOList.firstOrNull()?.journalpostId ?: "",
                        dokumentInfoId = manuellOppgaveDTOList.firstOrNull()?.dokumentInfoId ?: "",
                        msgId = manuellOppgaveDTOList.firstOrNull()?.sykmeldingId ?: "",
                        accessToken = accessToken,
                        oppgaveId = oppgaveId
                    )

                    if (!manuellOppgaveDTOList.firstOrNull()?.fnr.isNullOrEmpty()) {
                        if (syfoTilgangsKontrollService.hasAccess(accessToken, manuellOppgaveDTOList.first().fnr!!, cluster)) {
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
                        } else {
                            log.warn(
                                "Veileder har ikkje tilgang, {}",
                                StructuredArguments.keyValue("oppgaveId", oppgaveId)
                            )
                            call.respond(HttpStatusCode.Unauthorized)
                        }
                    } else {
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
                    }
                }
            }
        }
    }
}
