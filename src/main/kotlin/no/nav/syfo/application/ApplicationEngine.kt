package no.nav.syfo.application

import io.ktor.application.ApplicationCallPipeline
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.InternalAPI
import no.nav.syfo.Environment
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.metrics.monitorHttpRequests

@InternalAPI
fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort) {
        routing {
            registerNaisApi(applicationState)
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
