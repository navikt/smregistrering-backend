package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.Veileder
import no.nav.syfo.log
import no.nav.syfo.model.FerdigstillOppgave
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.OppgaveStatus
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.sykmelding.SykmeldingJobService
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
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
            tilordnetRessurs = veileder.veilederIdent
        )

        val ferdigstiltOppgave = oppgaveClient.ferdigstillOppgave(ferdigstillOppgave, sykmeldingId)

        if (shouldCreateOppfolgingsOppgave(receivedSykmelding)) {
            val createOppfolgingsoppgave = createOppfolgingsoppgave(receivedSykmelding, navEnhet, veileder)
            val opprettOppgave = oppgaveClient.opprettOppgave(createOppfolgingsoppgave, sykmeldingId)
            log.info("Opprettet oppfølgingsoppgave med id {} for sykmeldingsId {}", opprettOppgave.id, sykmeldingId)
        }

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

private fun shouldCreateOppfolgingsOppgave(receivedSykmelding: ReceivedSykmelding): Boolean {
    return receivedSykmelding.merknader?.isNotEmpty() == true
}

fun createOppfolgingsoppgave(receivedSykmelding: ReceivedSykmelding, enhet: String, veileder: Veileder): Oppgave =
    Oppgave(
        aktoerId = receivedSykmelding.sykmelding.pasientAktoerId,
        opprettetAvEnhetsnr = "9999",
        tilordnetRessurs = null,
        behandlesAvApplikasjon = "FS22",
        beskrivelse = "Oppfølgingsoppgave for tilbakedatert papirsykmelding",
        tema = "SYM",
        oppgavetype = "BEH_EL_SYM",
        behandlingstype = "ae0239",
        aktivDato = LocalDate.now(),
        fristFerdigstillelse = LocalDate.now(),
        prioritet = "HOY"
    )
