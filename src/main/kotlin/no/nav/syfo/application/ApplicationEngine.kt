package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.util.InternalAPI
import no.nav.syfo.Environment
import no.nav.syfo.aksessering.api.hentFerdigstiltSykmelding
import no.nav.syfo.aksessering.api.hentPapirSykmeldingManuellOppgave
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.controllers.AvvisPapirsykmeldingController
import no.nav.syfo.controllers.FerdigstiltSykmeldingController
import no.nav.syfo.controllers.SendPapirsykmeldingController
import no.nav.syfo.controllers.SendTilGosysController
import no.nav.syfo.log
import no.nav.syfo.metrics.monitorHttpRequests
import no.nav.syfo.pasient.api.pasientApi
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.api.avvisOppgave
import no.nav.syfo.persistering.api.endreSykmelding
import no.nav.syfo.persistering.api.sendOppgaveTilGosys
import no.nav.syfo.persistering.api.sendPapirSykmeldingManuellOppgave
import no.nav.syfo.persistering.db.ManuellOppgaveDAO
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.sykmelder.api.sykmelderApi
import no.nav.syfo.sykmelder.service.SykmelderService

@InternalAPI
fun createApplicationEngine(
    env: Environment,
    sendPapirsykmeldingController: SendPapirsykmeldingController,
    applicationState: ApplicationState,
    jwkProvider: JwkProvider,
    manuellOppgaveDAO: ManuellOppgaveDAO,
    safDokumentClient: SafDokumentClient,
    sendTilGosysController: SendTilGosysController,
    avvisPapirsykmeldingController: AvvisPapirsykmeldingController,
    ferdigstiltSykmeldingController: FerdigstiltSykmeldingController,
    pdlService: PdlPersonService,
    sykmelderService: SykmelderService,
    authorizationService: AuthorizationService,
): ApplicationEngine =
    embeddedServer(
        Netty,
        env.applicationPort,
        configure = {
            // Increase timeout of Netty to handle large content bodies
            requestReadTimeoutSeconds = 10
            responseWriteTimeoutSeconds = 40
        }
    ) {
        setupAuth(env, jwkProvider, env.jwtIssuer)
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                log.error("Caught exception", cause)
                throw cause
            }
        }

        install(CORS) {
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Options)
            allowHeader("Content-Type")
            allowHost(env.smregistreringUrl, schemes = listOf("http", "https"))
            allowCredentials = true
        }

        routing {
            registerNaisApi(applicationState)
            authenticate("jwt") {
                hentPapirSykmeldingManuellOppgave(
                    manuellOppgaveDAO,
                    safDokumentClient,
                    sendTilGosysController,
                    authorizationService,
                )
                hentFerdigstiltSykmelding(ferdigstiltSykmeldingController)
                sendPapirSykmeldingManuellOppgave(sendPapirsykmeldingController)
                endreSykmelding(sendPapirsykmeldingController)
                avvisOppgave(avvisPapirsykmeldingController)
                pasientApi(pdlService, authorizationService)
                sykmelderApi(sykmelderService)
                sendOppgaveTilGosys(manuellOppgaveDAO, sendTilGosysController, authorizationService)
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
