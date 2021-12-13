package no.nav.syfo.persistering

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.metrics.SENT_TO_GOSYS_COUNTER
import no.nav.syfo.model.Utfall
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.LoggingMeta

suspend fun handleSendOppgaveTilGosys(
    authorizationService: AuthorizationService,
    oppgaveClient: OppgaveClient,
    manuellOppgaveService: ManuellOppgaveService,
    loggingMeta: LoggingMeta,
    oppgaveId: Int,
    accessToken: String
) {
    val veileder = authorizationService.getVeileder(accessToken)

    log.info("Sender oppgave med id $oppgaveId til Gosys {}", fields(loggingMeta))

    oppgaveClient.sendOppgaveTilGosys(
        oppgaveId = oppgaveId,
        msgId = loggingMeta.msgId,
        tilordnetRessurs = veileder.veilederIdent
    )
    manuellOppgaveService.ferdigstillManuellOppgave(oppgaveId = oppgaveId, utfall = Utfall.SENDT_TIL_GOSYS, ferdigstiltAv = veileder.veilederIdent)

    SENT_TO_GOSYS_COUNTER.inc()

    log.info("Ferdig å sende oppgave med id $oppgaveId til Gosys {}", fields(loggingMeta))
}
