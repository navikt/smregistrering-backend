package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.Veileder
import no.nav.syfo.log
import no.nav.syfo.model.FerdigstillOppgave
import no.nav.syfo.model.OppgaveStatus
import no.nav.syfo.model.Sykmelder
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
suspend fun handleAvvisOppgave(
    dokArkivClient: DokArkivClient,
    oppgaveClient: OppgaveClient,
    loggingMeta: LoggingMeta,
    sykmeldingId: String,
    journalpostId: String,
    dokumentInfoId: String?,
    oppgaveId: Int,
    veileder: Veileder,
    pasientFnr: String,
    sykmelder: Sykmelder,
    navEnhet: String
) {
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

    val oppgave = oppgaveClient.hentOppgave(oppgaveId, sykmeldingId)

    val ferdigstillOppgave = FerdigstillOppgave(
        versjon = oppgave.versjon ?: throw RuntimeException("Fant ikke versjon for oppgave ${oppgave.id}, sykmeldingId $sykmeldingId"),
        id = oppgaveId,
        status = OppgaveStatus.FERDIGSTILT,
        tildeltEnhetsnr = navEnhet,
        tilordnetRessurs = veileder.veilederIdent,
        mappeId = if (oppgave.tildeltEnhetsnr == navEnhet) {
            oppgave.mappeId
        } else {
            // Det skaper trøbbel i Oppgave-apiet hvis enheten som blir satt ikke har den aktuelle mappen
            null
        }
    )

    val ferdigStiltOppgave = oppgaveClient.ferdigstillOppgave(ferdigstillOppgave, sykmeldingId)
    log.info(
        "Ferdigstiller oppgave med {}, {}",
        StructuredArguments.keyValue("oppgaveId", ferdigStiltOppgave.id),
        StructuredArguments.fields(loggingMeta)
    )
}
