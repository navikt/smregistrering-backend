package no.nav.syfo.controllers

import io.ktor.http.HttpStatusCode
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.model.PapirManuellOppgave
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.exception.SafForbiddenException
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.syfosmregister.SyfosmregisterService

class FerdigstiltSykmeldingController(
    val manuellOppgaveDAO: ManuellOppgaveDAO,
    val safDokumentClient: SafDokumentClient,
    val syfosmregisterService: SyfosmregisterService,
    val authorizationService: AuthorizationService
) {

    suspend fun hentFerdigstiltOppgave(accessToken: String?, sykmeldingId: String?): HttpServiceResponse {

        val ferdigstilteOppgaver = sykmeldingId?.let {
            manuellOppgaveDAO.hentFerdigstiltManuellOppgave(it)
        } ?: emptyList()

        when {
            accessToken == null -> {
                log.info("Mangler JWT Bearer token i HTTP header")
                return HttpServiceResponse(HttpStatusCode.Unauthorized)
            }
            sykmeldingId == null -> {
                log.info("Ugyldig path parameter: sykmeldingId")
                return HttpServiceResponse(HttpStatusCode.BadRequest)
            }
            ferdigstilteOppgaver.isEmpty() -> {
                return fetchFromSyfosmregister(sykmeldingId, accessToken)
            }
            else -> return handleLocalOppgave(sykmeldingId, accessToken, ferdigstilteOppgaver)
        }
    }

    private suspend fun fetchFromSyfosmregister(sykmeldingId: String, accessToken: String): HttpServiceResponse {

        val hentSykmelding = syfosmregisterService.hentSykmelding(sykmeldingId)
        log.info("Hentet sykmelding fra syfosmregister, sykmelding: ${hentSykmelding.sykmelding.id}")

        if (!authorizationService.hasSuperuserAccess(accessToken, hentSykmelding.pasientFnr)) {
            log.warn(
                "Veileder har ikke tilgang til å åpne ferdigstilt oppgave, {}",
                StructuredArguments.keyValue("sykmeldingId", sykmeldingId)
            )
            return HttpServiceResponse(HttpStatusCode.Forbidden, "Veileder har ikke tilgang til å endre oppgaver")
        } else {

        }

        return HttpServiceResponse(HttpStatusCode.InternalServerError)
    }

    private suspend fun handleLocalOppgave(
        sykmeldingId: String,
        accessToken: String,
        ferdigstilteOppgaver: List<ManuellOppgaveDTO>
    ): HttpServiceResponse {
        log.info(
            "Hentet ut ferdigstilt manuelloppgave med {}",
            StructuredArguments.keyValue("sykmeldingId", sykmeldingId)
        )

        if (!ferdigstilteOppgaver.firstOrNull()?.fnr.isNullOrEmpty()) {

            val manuellOppgave = ferdigstilteOppgaver.first()
            val fnr = manuellOppgave.fnr!!

            if (!authorizationService.hasSuperuserAccess(accessToken, fnr)) {
                log.warn(
                    "Veileder har ikke tilgang til å åpne ferdigstilt oppgave, {}",
                    StructuredArguments.keyValue("sykmeldingId", sykmeldingId)
                )
                return HttpServiceResponse(HttpStatusCode.Forbidden, "Veileder har ikke tilgang til å endre oppgaver")
            }

            val receivedSykmelding = manuellOppgaveDAO.hentSykmelding(sykmeldingId)
                ?: return HttpServiceResponse(HttpStatusCode.NotFound, "Fant ingen ferdigstilte manuelloppgaver med sykmeldingId $sykmeldingId")

            val sykmelding = receivedSykmelding!!.sykmelding

            val ferdigstilteOppgaver = ferdigstilteOppgaver.first()
            try {
                val pdfPapirSykmelding = safDokumentClient.hentDokument(
                    journalpostId = ferdigstilteOppgaver.journalpostId,
                    dokumentInfoId = ferdigstilteOppgaver.dokumentInfoId ?: "",
                    msgId = ferdigstilteOppgaver.sykmeldingId,
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

                    return HttpServiceResponse(HttpStatusCode.OK, papirManuellOppgave)
                } else {
                    log.error("Fant ikke PDF for sykmeldingId $sykmeldingId")
                    return HttpServiceResponse(HttpStatusCode.InternalServerError)
                }
            } catch (safForbiddenException: SafForbiddenException) {
                return HttpServiceResponse(HttpStatusCode.Forbidden, "Du har ikke tilgang til dokumentet i SAF")
            }
        }
        return HttpServiceResponse(HttpStatusCode.InternalServerError)
    }
}
