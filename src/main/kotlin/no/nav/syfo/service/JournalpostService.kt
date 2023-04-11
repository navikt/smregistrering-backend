package no.nav.syfo.service

import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.log
import no.nav.syfo.model.FerdigstillRegistrering
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.util.LoggingMeta

class JournalpostService(
    private val dokArkivClient: DokArkivClient,
    private val safJournalpostService: SafJournalpostService,
) {

    suspend fun ferdigstillJournalpost(
        accessToken: String,
        ferdigstillRegistrering: FerdigstillRegistrering,
        receivedSykmelding: ReceivedSykmelding?,
        loggingMeta: LoggingMeta,
    ) {
        if (!safJournalpostService.erJournalfoert(journalpostId = ferdigstillRegistrering.journalpostId, token = accessToken)) {
            dokArkivClient.oppdaterOgFerdigstillJournalpost(
                journalpostId = ferdigstillRegistrering.journalpostId,
                dokumentInfoId = ferdigstillRegistrering.dokumentInfoId,
                pasientFnr = ferdigstillRegistrering.pasientFnr,
                sykmeldingId = ferdigstillRegistrering.sykmeldingId,
                sykmelder = ferdigstillRegistrering.sykmelder,
                loggingMeta = loggingMeta,
                navEnhet = ferdigstillRegistrering.navEnhet,
                avvist = ferdigstillRegistrering.avvist,
                receivedSykmelding = receivedSykmelding,
            )
        } else {
            log.info(
                "Hopper over oppdaterOgFerdigstillJournalpost, " +
                    "journalpostId ${ferdigstillRegistrering.journalpostId} er allerede journalført",
            )
        }
    }
}
