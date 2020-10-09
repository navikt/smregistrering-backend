package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.Veileder
import no.nav.syfo.log
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

    val oppgaveVersjon = oppgaveClient.hentOppgave(oppgaveId, sykmeldingId).versjon

    val ferdigStillOppgave = ferdigStillOppgave(oppgaveId, oppgaveVersjon, veileder.veilederIdent, navEnhet)

    val oppgaveResponse = oppgaveClient.ferdigStillOppgave(ferdigStillOppgave, sykmeldingId)
    log.info(
        "Ferdigstiller oppgave med {}, {}",
        StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
        StructuredArguments.fields(loggingMeta)
    )
}
