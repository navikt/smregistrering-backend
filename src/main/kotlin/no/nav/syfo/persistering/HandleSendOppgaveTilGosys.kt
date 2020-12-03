package no.nav.syfo.persistering

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.LoggingMeta

suspend fun handleSendOppgaveTilGosys(
    authorizationService: AuthorizationService,
    oppgaveClient: OppgaveClient,
    manuellOppgaveService: ManuellOppgaveService,
    loggingMeta: LoggingMeta,
    oppgaveId: Int,
    navEnhet: String?,
    accessToken: String
) {
    val veileder = authorizationService.getVeileder(accessToken)

    log.info("Sender oppgave med id $oppgaveId til Gosys {}", fields(loggingMeta))

    oppgaveClient.sendOppgaveTilGosys(
        oppgaveId = oppgaveId,
        msgId = loggingMeta.msgId,
        tildeltEnhetsnr = navEnhet,
        tilordnetRessurs = veileder.veilederIdent
    )
    manuellOppgaveService.ferdigstillSmRegistering(oppgaveId)

    log.info("Ferdig å sende oppgave med id $oppgaveId til Gosys {}", fields(loggingMeta))
}
