package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions

@KtorExperimentalAPI
suspend fun handleRecivedMessage(
    papirSmRegistering: PapirSmRegistering,
    loggingMeta: LoggingMeta
) {
    wrapExceptions(loggingMeta) {
        log.info("Mottok ein manuell papir sykmelding registering, {}", fields(loggingMeta))
        INCOMING_MESSAGE_COUNTER.inc()
    }
}
