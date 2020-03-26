package no.nav.syfo

import io.ktor.util.InternalAPI
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smregisteringbackend")

@InternalAPI
@KtorExperimentalAPI
fun main() {
    val environment = Environment()

    val applicationState = ApplicationState()

    val applicationEngine = createApplicationEngine(
        environment, applicationState)
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    applicationServer.start()
    applicationState.ready = true

    log.info("Hello World")
}
