package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.application.port
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import no.nav.syfo.Environment
import no.nav.syfo.aksessering.api.hentFerdigstiltSykmelding
import no.nav.syfo.aksessering.api.hentPapirSykmeldingManuellOppgave
import no.nav.syfo.aksessering.api.hentPapirSykmeldingManuellOppgaveTilSykDig
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.controllers.AvvisPapirsykmeldingController
import no.nav.syfo.controllers.FerdigstiltSykmeldingController
import no.nav.syfo.controllers.SendPapirsykmeldingController
import no.nav.syfo.controllers.SendTilGosysController
import no.nav.syfo.log
import no.nav.syfo.metrics.monitorHttpRequests
import no.nav.syfo.pasient.api.pasientApi
import no.nav.syfo.pdf.PdfService
import no.nav.syfo.pdf.registerPdfRoutes
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
import no.nav.syfo.sykmelding.SendtSykmeldingService

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
    pdfService: PdfService,
    sendtSykmeldingService: SendtSykmeldingService
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
    embeddedServer(
        Netty,
        configure = {
            // Increase timeout of Netty to handle large content bodies
            requestReadTimeoutSeconds = 15
            responseWriteTimeoutSeconds = 40
            connector { port = env.applicationPort }
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
                registerPdfRoutes(pdfService)
                hentPapirSykmeldingManuellOppgaveTilSykDig(
                    manuellOppgaveDAO,
                    sendtSykmeldingService
                )
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
