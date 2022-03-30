package no.nav.syfo.controllers

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.persistering.db.erOpprettManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions

class ReceivedSykmeldingController(
    private val database: DatabaseInterface,
    private val oppgaveService: OppgaveService,
) {

    suspend fun handleReceivedSykmelding(papirSmRegistering: PapirSmRegistering, loggingMeta: LoggingMeta) {
        wrapExceptions(loggingMeta) {
            log.info("Mottok ein manuell papirsykmelding registerings, {}", StructuredArguments.fields(loggingMeta))
            INCOMING_MESSAGE_COUNTER.inc()

            if (database.erOpprettManuellOppgave(papirSmRegistering.sykmeldingId)) {
                log.warn(
                    "Manuell papirsykmelding registeringsoppgave med sykmeldingsid {}, er allerede lagret i databasen, {}",
                    papirSmRegistering.sykmeldingId, StructuredArguments.fields(loggingMeta)
                )
            } else {
                val oppgave = oppgaveService.upsertOppgave(papirSmRegistering, loggingMeta)
                database.opprettManuellOppgave(papirSmRegistering, oppgave.id!!)
                log.info(
                    "Manuell papirsykmeldingoppgave lagret i databasen, for {}, {}",
                    StructuredArguments.keyValue("oppgaveId", oppgave.id),
                    StructuredArguments.fields(loggingMeta)
                )
                MESSAGE_STORED_IN_DB_COUNTER.inc()
            }
        }
    }
}
