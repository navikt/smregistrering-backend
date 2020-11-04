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
    oppgaveId: Int,
    veileder: Veileder,
    pasientFnr: String,
    sykmelder: Sykmelder,
    navEnhet: String
) {
    dokArkivClient.oppdaterOgFerdigstillJournalpost(
        journalpostId,
        pasientFnr,
        sykmeldingId,
        sykmelder,
        loggingMeta,
        navEnhet
    )

    val oppgaveVersjon = oppgaveClient.hentOppgaveVersjon(oppgaveId, sykmeldingId)

    val ferdigstillOppgave = FerdigstillOppgave(
        versjon = oppgaveVersjon,
        id = oppgaveId,
        status = OppgaveStatus.FERDIGSTILT,
        tildeltEnhetsnr = navEnhet,
        tilordnetRessurs = veileder.veilederIdent
    )

    val oppgave = oppgaveClient.ferdigstillOppgave(ferdigstillOppgave, sykmeldingId)
    log.info(
        "Ferdigstiller oppgave med {}, {}",
        StructuredArguments.keyValue("oppgaveId", oppgave.id),
        StructuredArguments.fields(loggingMeta)
    )
}
