package no.nav.syfo.persistering

import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.finnFristForFerdigstillingAvOppgave
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.metrics.OPPRETT_OPPGAVE_COUNTER
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.persistering.db.erOpprettManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions
import java.time.LocalDate

suspend fun handleRecivedMessage(
    papirSmRegistering: PapirSmRegistering,
    database: DatabaseInterface,
    oppgaveClient: OppgaveClient,
    loggingMeta: LoggingMeta
) {
    wrapExceptions(loggingMeta) {
        log.info("Mottok ein manuell papirsykmelding registerings, {}", fields(loggingMeta))
        INCOMING_MESSAGE_COUNTER.inc()

        if (database.erOpprettManuellOppgave(papirSmRegistering.sykmeldingId)) {
            log.warn(
                "Manuell papirsykmelding registeringsoppgave med sykmeldingsid {}, er allerede lagret i databasen, {}",
                papirSmRegistering.sykmeldingId, fields(loggingMeta)
            )
        } else {
            val oppgave = upsertOppgave(papirSmRegistering, oppgaveClient, loggingMeta)
            database.opprettManuellOppgave(papirSmRegistering, oppgave.id!!)
            log.info(
                "Manuell papirsykmeldingoppgave lagret i databasen, for {}, {}",
                StructuredArguments.keyValue("oppgaveId", oppgave.id),
                fields(loggingMeta)
            )
            MESSAGE_STORED_IN_DB_COUNTER.inc()
        }
    }
}

private suspend fun upsertOppgave(
    papirSmRegistering: PapirSmRegistering,
    oppgaveClient: OppgaveClient,
    loggingMeta: LoggingMeta
): Oppgave {
    return if (papirSmRegistering.oppgaveId == null || (papirSmRegistering.oppgaveId.toIntOrNull() == null)) {
        val opprettOppgave = OpprettOppgave(
            aktoerId = papirSmRegistering.aktorId,
            opprettetAvEnhetsnr = "9999",
            behandlesAvApplikasjon = "SMR",
            beskrivelse = "Manuell registrering av sykmelding mottatt på papir",
            tema = "SYM",
            oppgavetype = "JFR",
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(
                LocalDate.now().plusDays(4)
            ),
            prioritet = "HOY",
            journalpostId = papirSmRegistering.journalpostId
        )

        val opprettetOppgave = oppgaveClient.opprettOppgave(opprettOppgave, papirSmRegistering.sykmeldingId)
        OPPRETT_OPPGAVE_COUNTER.inc()
        log.info(
            "Opprettet manuell papirsykmeldingoppgave med {}, {}",
            StructuredArguments.keyValue("oppgaveId", opprettetOppgave.id),
            fields(loggingMeta)
        )
        opprettetOppgave
    } else {
        val oppdatertOppgave = oppgaveClient.patchManuellOppgave(
            papirSmRegistering.oppgaveId.toInt(),
            loggingMeta.msgId
        )
        log.info(
            "Patchet manuell papirsykmeldingsoppgave med {}, {}",
            StructuredArguments.keyValue("oppgaveId", oppdatertOppgave.id),
            fields(loggingMeta)
        )
        oppdatertOppgave
    }
}
