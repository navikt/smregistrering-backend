package no.nav.syfo.persistering

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.model.FerdigstillOppgave
import no.nav.syfo.model.OppgaveStatus
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.Veileder
import no.nav.syfo.util.LoggingMeta

suspend fun handleAvvisOppgave(
    dokArkivClient: DokArkivClient,
    oppgaveClient: OppgaveClient,
    safJournalpostService: SafJournalpostService,
    loggingMeta: LoggingMeta,
    sykmeldingId: String,
    journalpostId: String,
    dokumentInfoId: String?,
    oppgaveId: Int,
    veileder: Veileder,
    pasientFnr: String,
    sykmelder: Sykmelder,
    navEnhet: String,
    accessToken: String,
    avvisSykmeldingReason: String?
) {

    if (!safJournalpostService.erJournalfoert(journalpostId = journalpostId, token = accessToken)) {
        dokArkivClient.oppdaterOgFerdigstillJournalpost(
            journalpostId = journalpostId,
            dokumentInfoId = dokumentInfoId,
            pasientFnr = pasientFnr,
            sykmeldingId = sykmeldingId,
            sykmelder = sykmelder,
            loggingMeta = loggingMeta,
            navEnhet = navEnhet,
            avvist = true
        )
    } else {
        log.info("Hopper over oppdaterOgFerdigstillJournalpost, journalpostId $journalpostId er allerede journalført")
    }

    val oppgave = oppgaveClient.hentOppgave(oppgaveId, sykmeldingId)

    if (OppgaveStatus.FERDIGSTILT.name != oppgave.status) {
        val oppdatertBeskrivelse = when {
            !avvisSykmeldingReason.isNullOrEmpty() -> "Avvist papirsykmelding med årsak: $avvisSykmeldingReason"
            else -> "Avvist papirsykmelding uten oppgitt årsak."
        }
        val ferdigstillOppgave = FerdigstillOppgave(
            versjon = oppgave.versjon
                ?: throw RuntimeException("Fant ikke versjon for oppgave ${oppgave.id}, sykmeldingId $sykmeldingId"),
            id = oppgaveId,
            status = OppgaveStatus.FERDIGSTILT,
            tildeltEnhetsnr = navEnhet,
            tilordnetRessurs = veileder.veilederIdent,
            mappeId = null,
            beskrivelse = "$oppdatertBeskrivelse\n${oppgave.beskrivelse}"
        )

        val ferdigStiltOppgave = oppgaveClient.ferdigstillOppgave(ferdigstillOppgave, sykmeldingId)
        log.info(
            "Ferdigstiller oppgave med {}, {}",
            StructuredArguments.keyValue("oppgaveId", ferdigStiltOppgave.id),
            StructuredArguments.fields(loggingMeta)
        )
    } else {
        log.info("Hopper over ferdigstillOppgave, oppgaveId $oppgaveId er allerede ${oppgave.status}")
    }
}
