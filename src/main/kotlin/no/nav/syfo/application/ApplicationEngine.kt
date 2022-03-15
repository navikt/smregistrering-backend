package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.InternalAPI
import no.nav.syfo.Environment
import no.nav.syfo.aksessering.api.hentFerdigstiltSykmelding
import no.nav.syfo.aksessering.api.hentPapirSykmeldingManuellOppgave
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.metrics.monitorHttpRequests
import no.nav.syfo.pasient.api.pasientApi
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.SendPapirsykmeldingService
import no.nav.syfo.persistering.api.avvisOppgave
import no.nav.syfo.persistering.api.endreSykmelding
import no.nav.syfo.persistering.api.sendOppgaveTilGosys
import no.nav.syfo.persistering.api.sendPapirSykmeldingManuellOppgave
import no.nav.syfo.saf.SafDokumentClient
import no.nav.syfo.saf.service.SafJournalpostService
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.sykmelder.api.sykmelderApi
import no.nav.syfo.sykmelder.service.SykmelderService

@InternalAPI
fun createApplicationEngine(
    env: Environment,
    sendPapirsykmeldingService: SendPapirsykmeldingService,
    applicationState: ApplicationState,
    jwkProvider: JwkProvider,
    manuellOppgaveService: ManuellOppgaveService,
    safDokumentClient: SafDokumentClient,
    oppgaveClient: OppgaveClient,
    dokArkivClient: DokArkivClient,
    safJournalpostService: SafJournalpostService,
    pdlService: PdlPersonService,
    sykmelderService: SykmelderService,
    authorizationService: AuthorizationService
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort, configure = {
        // Increase timeout of Netty to handle large content bodies
        responseWriteTimeoutSeconds = 40
    }) {
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
            exception<Throwable> { cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                log.error("Caught exception", cause)
                throw cause
            }
        }

        install(CORS) {
            method(HttpMethod.Get)
            method(HttpMethod.Post)
            method(HttpMethod.Put)
            method(HttpMethod.Options)
            header("Content-Type")
            host(env.smregistreringUrl, schemes = listOf("http", "https"))
            allowCredentials = true
        }

        routing {
            registerNaisApi(applicationState)
            authenticate("jwt") {
                hentPapirSykmeldingManuellOppgave(manuellOppgaveService, safDokumentClient, oppgaveClient, authorizationService)
                hentFerdigstiltSykmelding(manuellOppgaveService, safDokumentClient, authorizationService)
                sendPapirSykmeldingManuellOppgave(sendPapirsykmeldingService)
                endreSykmelding(sendPapirsykmeldingService)
                avvisOppgave(
                    manuellOppgaveService,
                    authorizationService,
                    safJournalpostService,
                    sykmelderService,
                    dokArkivClient,
                    oppgaveClient
                )
                pasientApi(pdlService, authorizationService)
                sykmelderApi(sykmelderService)
                sendOppgaveTilGosys(manuellOppgaveService, authorizationService, oppgaveClient)
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
