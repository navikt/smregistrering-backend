package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.PapirManuellOppgave
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.exception.SafForbiddenException
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader

fun Route.hentFerdigstiltSykmelding(
    manuellOppgaveService: ManuellOppgaveService,
    safDokumentClient: SafDokumentClient,
    authorizationService: AuthorizationService
) {
    route("/api/v1") {
        get("/sykmelding/{sykmeldingId}/ferdigstilt") {
            val sykmeldingId = call.parameters["sykmeldingId"]

            log.info("Mottok kall til GET /api/v1/sykmelding/$sykmeldingId/ferdigstilt")

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val ferdigstilteOppgaver = sykmeldingId?.let {
                manuellOppgaveService.hentFerdigstiltManuellOppgave(it)
            } ?: emptyList()
            when {
                accessToken == null -> {
                    log.info("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.Unauthorized)
                }
                sykmeldingId == null -> {
                    log.info("Ugyldig path parameter: sykmeldingId")
                    call.respond(HttpStatusCode.BadRequest)
                }
                ferdigstilteOppgaver.isEmpty() -> {
                    log.info(
                        "Fant ingen ferdigstilte manuelloppgaver for sykmledingId {}",
                        StructuredArguments.keyValue("sykmeldingId", sykmeldingId)
                    )
                    call.respond(
                        HttpStatusCode.NotFound,
                        "Fant ingen ferdigstilte manuelloppgaver med sykmeldingId $sykmeldingId"
                    )
                }
                else -> {
                    log.info(
                        "Henter ut ferdigstilt manuelloppgave med {}",
                        StructuredArguments.keyValue("sykmeldingId", sykmeldingId)
                    )

                    if (!ferdigstilteOppgaver.firstOrNull()?.fnr.isNullOrEmpty()) {

                        val manuellOppgave = ferdigstilteOppgaver.first()
                        val fnr = manuellOppgave.fnr!!

                        if (!authorizationService.hasAccess(accessToken, fnr)) {
                            log.warn(
                                "Veileder har ikke tilgang, {}",
                                StructuredArguments.keyValue("sykmeldingId", sykmeldingId)
                            )
                            call.respond(HttpStatusCode.Forbidden, "Veileder har ikke tilgang til oppgaven")
                            return@get
                        }

                        val receivedSykmelding = manuellOppgaveService.hentSykmelding(sykmeldingId)

                        if (receivedSykmelding == null) {
                            call.respond(
                                HttpStatusCode.NotFound,
                                "Fant ingen ferdigstilte manuelloppgaver med sykmeldingId $sykmeldingId"
                            )
                        }

                        val sykmelding = receivedSykmelding!!.sykmelding

                        try {
                            val pdfPapirSykmelding = safDokumentClient.hentDokument(
                                journalpostId = ferdigstilteOppgaver.first().journalpostId,
                                dokumentInfoId = ferdigstilteOppgaver.first().dokumentInfoId ?: "",
                                msgId = ferdigstilteOppgaver.first().sykmeldingId,
                                accessToken = accessToken,
                                oppgaveId = manuellOppgave.oppgaveid
                            )
                            if (pdfPapirSykmelding != null) {
                                val papirSmRegistering = PapirSmRegistering(
                                    journalpostId = manuellOppgave.journalpostId,
                                    oppgaveId = manuellOppgave.oppgaveid.toString(),
                                    fnr = manuellOppgave.fnr,
                                    aktorId = manuellOppgave.aktorId,
                                    dokumentInfoId = manuellOppgave.dokumentInfoId,
                                    datoOpprettet = manuellOppgave.datoOpprettet,
                                    sykmeldingId = manuellOppgave.sykmeldingId,
                                    syketilfelleStartDato = sykmelding.syketilfelleStartDato,
                                    arbeidsgiver = sykmelding.arbeidsgiver,
                                    medisinskVurdering = sykmelding.medisinskVurdering,
                                    skjermesForPasient = sykmelding.skjermesForPasient,
                                    perioder = sykmelding.perioder,
                                    prognose = sykmelding.prognose,
                                    utdypendeOpplysninger = sykmelding.utdypendeOpplysninger,
                                    tiltakNAV = sykmelding.tiltakNAV,
                                    tiltakArbeidsplassen = sykmelding.tiltakArbeidsplassen,
                                    andreTiltak = sykmelding.andreTiltak,
                                    meldingTilNAV = sykmelding.meldingTilNAV,
                                    meldingTilArbeidsgiver = sykmelding.meldingTilArbeidsgiver,
                                    kontaktMedPasient = sykmelding.kontaktMedPasient,
                                    behandletTidspunkt = sykmelding.behandletTidspunkt.toLocalDate(),
                                    behandler = sykmelding.behandler
                                )

                                val papirManuellOppgave = PapirManuellOppgave(
                                    fnr = ferdigstilteOppgaver.first().fnr,
                                    sykmeldingId = ferdigstilteOppgaver.first().sykmeldingId,
                                    oppgaveid = ferdigstilteOppgaver.first().oppgaveid,
                                    pdfPapirSykmelding = pdfPapirSykmelding,
                                    papirSmRegistering = papirSmRegistering
                                )

                                call.respond(papirManuellOppgave)
                            } else {
                                log.error("Fant ikke PDF for sykmeldingId $sykmeldingId")
                                call.respond(HttpStatusCode.InternalServerError)
                            }
                        } catch (safForbiddenException: SafForbiddenException) {
                            call.respond(HttpStatusCode.Forbidden, "Du har ikke tilgang til dokumentet i SAF")
                        }
                    }
                }
            }
        }
    }
}
