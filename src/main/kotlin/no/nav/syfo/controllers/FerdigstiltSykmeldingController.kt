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
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.syfosmregister.SyfosmregisterService
import no.nav.syfo.util.LoggingMeta

class FerdigstiltSykmeldingController(
    val manuellOppgaveDAO: ManuellOppgaveDAO,
    val safDokumentClient: SafDokumentClient,
    val syfosmregisterService: SyfosmregisterService,
    val authorizationService: AuthorizationService,
    val safJournalpostService: SafJournalpostService,
    val receivedSykmeldingController: ReceivedSykmeldingController
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
            !ferdigstilteOppgaver.first().ferdigstilt -> {
                return HttpServiceResponse(HttpStatusCode.NotFound, "Oppgaven er ikke ferdigstilt")
            }
            else -> return handleLocalOppgave(sykmeldingId, accessToken, ferdigstilteOppgaver)
        }
    }

    private suspend fun fetchFromSyfosmregister(sykmeldingId: String, accessToken: String): HttpServiceResponse {

        val papirSykmelding = syfosmregisterService.hentSykmelding(sykmeldingId)
            ?: return HttpServiceResponse(HttpStatusCode.NotFound, "Fant ikke sykmelding $sykmeldingId")

        log.info("Hentet sykmelding fra syfosmregister, sykmelding: ${papirSykmelding.sykmelding.id}")

        if (!authorizationService.hasSuperuserAccess(accessToken, papirSykmelding.pasientFnr)) {
            log.warn(
                "Veileder har ikke tilgang til å åpne ferdigstilt oppgave, {}",
                StructuredArguments.keyValue("sykmeldingId", sykmeldingId)
            )
            return HttpServiceResponse(HttpStatusCode.Forbidden, "Veileder har ikke tilgang til å endre oppgaver")
        } else {
            val journalpostId = papirSykmelding.sykmelding.avsenderSystem.versjon
            val journalPost = safJournalpostService.getJournalPostDokumentInfo(journalpostId, accessToken)
            val dokuments = journalPost.data.journalpost.dokumenter
            if (dokuments.size != 1) {
                log.error("Journalpost for papirsykmelding har ${dokuments.size} dokumenter")
                return HttpServiceResponse(HttpStatusCode.UnprocessableEntity, "Det ser ut som det er feil antall dokumenter på journalposten")
            }

            val dokumentInfoId = dokuments.first()
            val dokument = safDokumentClient.hentDokument(
                journalpostId, dokumentInfoId.dokumentInfoId,
                papirSykmelding.sykmelding.id, accessToken, sykmeldingId
            )

            if (dokument == null) {
                log.error("Fant ikke PDF for sykmeldingId $sykmeldingId")
                return HttpServiceResponse(HttpStatusCode.InternalServerError)
            }

            val papirSmRegistering = PapirSmRegistering(
                journalpostId = journalpostId,
                oppgaveId = Int.MAX_VALUE.toString(),
                fnr = papirSykmelding.pasientFnr,
                aktorId = papirSykmelding.pasientAktoerId,
                dokumentInfoId = dokumentInfoId.dokumentInfoId,
                datoOpprettet = papirSykmelding.mottattTidspunkt,
                sykmeldingId = papirSykmelding.sykmelding.id,
                syketilfelleStartDato = papirSykmelding.sykmelding.syketilfelleStartDato,
                arbeidsgiver = papirSykmelding.sykmelding.arbeidsgiver,
                medisinskVurdering = papirSykmelding.sykmelding.medisinskVurdering,
                skjermesForPasient = papirSykmelding.sykmelding.skjermesForPasient,
                perioder = papirSykmelding.sykmelding.perioder,
                prognose = papirSykmelding.sykmelding.prognose,
                utdypendeOpplysninger = papirSykmelding.sykmelding.utdypendeOpplysninger,
                tiltakNAV = papirSykmelding.sykmelding.tiltakNAV,
                tiltakArbeidsplassen = papirSykmelding.sykmelding.tiltakArbeidsplassen,
                andreTiltak = papirSykmelding.sykmelding.andreTiltak,
                meldingTilNAV = papirSykmelding.sykmelding.meldingTilNAV,
                meldingTilArbeidsgiver = papirSykmelding.sykmelding.meldingTilArbeidsgiver,
                kontaktMedPasient = papirSykmelding.sykmelding.kontaktMedPasient,
                behandletTidspunkt = papirSykmelding.sykmelding.behandletTidspunkt.toLocalDate(),
                behandler = papirSykmelding.sykmelding.behandler
            )

            receivedSykmeldingController.handlePapirsykmeldingFromSyfosmregister(
                papirSykmelding,
                papirSmRegistering,
                LoggingMeta(
                    mottakId = sykmeldingId,
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId.dokumentInfoId,
                    msgId = papirSykmelding.sykmelding.msgId,
                    sykmeldingId = sykmeldingId
                )
            )

            val papirManuellOppgave = PapirManuellOppgave(
                fnr = papirSykmelding.pasientFnr,
                sykmeldingId = papirSykmelding.sykmelding.id,
                oppgaveid = Int.MAX_VALUE,
                pdfPapirSykmelding = dokument,
                papirSmRegistering = papirSmRegistering
            )

            return HttpServiceResponse(HttpStatusCode.OK, papirManuellOppgave)
        }
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

            val sykmelding = receivedSykmelding.sykmelding

            try {
                val pdfPapirSykmelding = safDokumentClient.hentDokument(
                    journalpostId = manuellOppgave.journalpostId,
                    dokumentInfoId = manuellOppgave.dokumentInfoId ?: "",
                    msgId = manuellOppgave.sykmeldingId,
                    accessToken = accessToken,
                    sykmeldingId = manuellOppgave.sykmeldingId
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
                        fnr = manuellOppgave.fnr,
                        sykmeldingId = manuellOppgave.sykmeldingId,
                        oppgaveid = manuellOppgave.oppgaveid!!,
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
