package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.finnFristForFerdigstillingAvOppgave
import no.nav.syfo.db.Database
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.metrics.OPPRETT_OPPGAVE_COUNTER
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.persistering.db.erOpprettManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions

@KtorExperimentalAPI
suspend fun handleRecivedMessage(
    papirSmRegistering: PapirSmRegistering,
    database: Database,
    oppgaveClient: OppgaveClient,
    loggingMeta: LoggingMeta
) {
    wrapExceptions(loggingMeta) {
        log.info("Mottok ein manuell papirsykmelding registerings, {}", fields(loggingMeta))
        INCOMING_MESSAGE_COUNTER.inc()

        if (database.erOpprettManuellOppgave(papirSmRegistering.sykmeldingId)) {
            log.warn(
                "Manuell papirsykmelding registerings oppgave med sykmeldingsid {}, er allerede lagret i databasen, {}",
                papirSmRegistering.sykmeldingId, fields(loggingMeta)
            )
        } else {
            // TODO må få ein egen behandlingstype for papirsykmeldinger
            val opprettOppgave = OpprettOppgave(
                aktoerId = papirSmRegistering.aktorId,
                opprettetAvEnhetsnr = "9999",
                behandlesAvApplikasjon = "FS22",
                beskrivelse = "Manuell papir sykmeldingoppgave, gjelder for journalpostid: ${papirSmRegistering.journalpostId}",
                tema = "SYM",
                oppgavetype = "BEH_EL_SYM",
                behandlingstype = "ae0239",
                aktivDato = LocalDate.now(),
                fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(LocalDate.now()),
                prioritet = "HOY"
            )

            val oppgaveResponse = oppgaveClient.opprettOppgave(opprettOppgave, papirSmRegistering.sykmeldingId)
            OPPRETT_OPPGAVE_COUNTER.inc()
            log.info(
                "Opprettet manuell papir sykmeldingoppgave med {}, {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
                fields(loggingMeta)
            )

            database.opprettManuellOppgave(papirSmRegistering, oppgaveResponse.id)
            log.info(
                "Manuell papir sykmeldingoppgave lagret i databasen, for {}, {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
                fields(loggingMeta)
            )
            MESSAGE_STORED_IN_DB_COUNTER.inc()
        }
    }
}
