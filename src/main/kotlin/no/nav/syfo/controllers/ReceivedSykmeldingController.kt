package no.nav.syfo.controllers

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.persistering.db.erOpprettManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.persistering.db.slettSykmelding
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.syfosmregister.papirsykmelding.model.PapirsykmeldingDTO
import no.nav.syfo.sykmelding.db.upsertSendtSykmelding
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions

class ReceivedSykmeldingController(
    private val database: DatabaseInterface,
    private val oppgaveService: OppgaveService,
) {

    suspend fun handleReceivedSykmelding(
        papirSmRegistering: PapirSmRegistering,
        loggingMeta: LoggingMeta
    ) {
        log.info(
            "Mottok manuell papirsykmelding ${papirSmRegistering.sykmeldingId} med journalpostId ${papirSmRegistering.journalpostId}"
        )
        wrapExceptions(loggingMeta) {
            log.info(
                "Mottok ein manuell papirsykmelding registerings, {}",
                StructuredArguments.fields(loggingMeta)
            )
            INCOMING_MESSAGE_COUNTER.inc()

            if (database.erOpprettManuellOppgave(papirSmRegistering.sykmeldingId)) {
                log.warn(
                    "Manuell papirsykmelding registeringsoppgave med sykmeldingsid {}, er allerede lagret i databasen, {}",
                    papirSmRegistering.sykmeldingId,
                )
            } else {
                val oppgave = oppgaveService.upsertOppgave(papirSmRegistering, loggingMeta)
                database.opprettManuellOppgave(papirSmRegistering, oppgave.id!!)
                log.info(
                    "Manuell papirsykmeldingoppgave lagret i databasen, for {}, {}",
                    StructuredArguments.keyValue("oppgaveId", oppgave.id),
                    StructuredArguments.fields(loggingMeta),
                )
                MESSAGE_STORED_IN_DB_COUNTER.inc()
            }
        }
    }

    suspend fun handlePapirsykmeldingFromSyfosmregister(
        papirSykmelding: PapirsykmeldingDTO,
        papirSmRegistering: PapirSmRegistering,
        loggingMeta: LoggingMeta,
    ) {
        wrapExceptions(loggingMeta) {
            log.info(
                "Mottok ein manuell papirsykmelding registerings from syfosmregister, {}",
                StructuredArguments.fields(loggingMeta)
            )
            database.opprettManuellOppgave(
                papirSmRegistering = papirSmRegistering,
                oppgaveId = null,
                ferdigstilt = true
            )
            database.upsertSendtSykmelding(papirSmRegistering.toReceveidSykmelding(papirSykmelding))
        }
    }

    fun slettSykmelding(sykmeldingId: String) {
        val antallSlettedeRader = database.slettSykmelding(sykmeldingId)
        if (antallSlettedeRader > 0) {
            log.info("Slettet sykmelding med id $sykmeldingId og tilhørende historikk")
        }
    }
}

private fun PapirSmRegistering.toReceveidSykmelding(
    papirSykmelding: PapirsykmeldingDTO
): ReceivedSykmelding {
    return ReceivedSykmelding(
        sykmelding = papirSykmelding.sykmelding,
        personNrPasient = papirSykmelding.pasientFnr,
        personNrLege = papirSykmelding.sykmelding.behandler.fnr,
        tlfPasient = null,
        legeHelsepersonellkategori = null,
        legeHprNr = null,
        navLogId = sykmeldingId,
        msgId = sykmeldingId,
        legekontorOrgNr = null,
        legekontorHerId = null,
        legekontorReshId = null,
        legekontorOrgName = "",
        mottattDato = papirSykmelding.mottattTidspunkt,
        rulesetVersion = "",
        merknader = emptyList(),
        partnerreferanse = "",
        fellesformat = "",
        tssid = "",
        vedlegg = null,
        utenlandskSykmelding = null,
    )
}
