package no.nav.syfo.persistering

import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.model.FerdigstillOppgave
import no.nav.syfo.model.OppgaveStatus
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.Veileder
import no.nav.syfo.sykmelding.SykmeldingJobService
import no.nav.syfo.util.LoggingMeta

suspend fun handleOKOppgave(
    sykmeldingJobService: SykmeldingJobService,
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta,
    oppgaveClient: OppgaveClient,
    dokArkivClient: DokArkivClient,
    safJournalpostService: SafJournalpostService,
    accessToken: String,
    sykmeldingId: String,
    journalpostId: String,
    dokumentInfoId: String?,
    oppgaveId: Int,
    veileder: Veileder,
    sykmelder: Sykmelder,
    navEnhet: String
) {

    sykmeldingJobService.upsertSykmelding(receivedSykmelding)

    if (!safJournalpostService.erJournalfoert(journalpostId = journalpostId, token = accessToken)) {
        dokArkivClient.oppdaterOgFerdigstillJournalpost(
            journalpostId = journalpostId,
            dokumentInfoId = dokumentInfoId,
            pasientFnr = receivedSykmelding.personNrPasient,
            sykmeldingId = sykmeldingId,
            sykmelder = sykmelder,
            loggingMeta = loggingMeta,
            navEnhet = navEnhet,
            avvist = false
        )
    } else {
        log.info("Hopper over oppdaterOgFerdigstillJournalpost, journalpostId $journalpostId er allerede journalført")
    }

    val oppgave = oppgaveClient.hentOppgave(oppgaveId, sykmeldingId)

    if (OppgaveStatus.FERDIGSTILT.name != oppgave.status) {
        val ferdigstillOppgave = FerdigstillOppgave(
            versjon = oppgave.versjon!!,
            id = oppgaveId,
            status = OppgaveStatus.FERDIGSTILT,
            tildeltEnhetsnr = navEnhet,
            tilordnetRessurs = veileder.veilederIdent,
            mappeId = null,
            beskrivelse = oppgave.beskrivelse
        )

        val ferdigstiltOppgave = oppgaveClient.ferdigstillOppgave(ferdigstillOppgave, sykmeldingId)

        sykmeldingJobService.createJobs(receivedSykmelding)

        log.info(
            "Ferdigstiller oppgave med {}, {}",
            keyValue("oppgaveId", ferdigstiltOppgave.id),
            fields(loggingMeta)
        )
    } else {
        log.info("Hopper over ferdigstillOppgave, oppgaveId $oppgaveId er allerede ${oppgave.status}")
    }
}
