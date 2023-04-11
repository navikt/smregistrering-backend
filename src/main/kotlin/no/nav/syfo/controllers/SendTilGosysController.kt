package no.nav.syfo.controllers

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.metrics.SENT_TO_GOSYS_COUNTER
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.Utfall
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.OppgaveService
import no.nav.syfo.util.LoggingMeta

class SendTilGosysController(
    private val authorizationService: AuthorizationService,
    private val manuellOppgaveDAO: ManuellOppgaveDAO,
    private val oppgaveService: OppgaveService,
) {

    suspend fun sendOppgaveTilGosys(
        oppgaveId: Int,
        sykmeldingId: String,
        accessToken: String,
        loggingMeta: LoggingMeta,
    ): Oppgave {
        val veileder = authorizationService.getVeileder(accessToken)

        log.info("Sender oppgave med id $oppgaveId til Gosys {}", StructuredArguments.fields(loggingMeta))

        val tilGosys = oppgaveService.sendOppgaveTilGosys(
            oppgaveId = oppgaveId,
            msgId = loggingMeta.msgId,
            tilordnetRessurs = veileder.veilederIdent,
        )
        manuellOppgaveDAO.ferdigstillSmRegistering(sykmeldingId = sykmeldingId, utfall = Utfall.SENDT_TIL_GOSYS, ferdigstiltAv = veileder.veilederIdent)

        SENT_TO_GOSYS_COUNTER.inc()

        log.info("Ferdig å sende oppgave med id $oppgaveId til Gosys {}", StructuredArguments.fields(loggingMeta))

        return tilGosys
    }
}
