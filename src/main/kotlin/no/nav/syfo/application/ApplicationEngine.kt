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
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.aksessering.api.hentPapirSykmeldingManuellOppgave
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.RegelClient
import no.nav.syfo.client.SafDokumentClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.metrics.monitorHttpRequests
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.api.ValidationException
import no.nav.syfo.persistering.api.sendPapirSykmeldingManuellOppgave
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.sykmelder.api.sykmelderApi
import no.nav.syfo.sykmelder.service.SykmelderService

@KtorExperimentalAPI
@InternalAPI
fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    vaultSecrets: VaultSecrets,
    jwkProvider: JwkProvider,
    issuer: String,
    manuellOppgaveService: ManuellOppgaveService,
    safDokumentClient: SafDokumentClient,
    kafkaProducers: KafkaProducers,
    oppgaveClient: OppgaveClient,
    kuhrsarClient: SarClient,
    dokArkivClient: DokArkivClient,
    regelClient: RegelClient,
    pdlService: PdlPersonService,
    sykmelderService: SykmelderService,
    authorizationService: AuthorizationService
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort, configure = {
        // Increase timeout of Netty to handle large content bodies
        responseWriteTimeoutSeconds = 40
    }) {
        setupAuth(vaultSecrets, jwkProvider, issuer)
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(StatusPages) {
            exception<ValidationException> { cause ->
                call.respond(HttpStatusCode.BadRequest, cause.validationResult)
                log.error("Caught ValidationException", cause)
            }

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
            host(env.smregistreringUrl, schemes = listOf("https", "https"))
            allowCredentials = true
        }
        routing {
            registerNaisApi(applicationState)
            authenticate("jwt") {
                hentPapirSykmeldingManuellOppgave(manuellOppgaveService, safDokumentClient, authorizationService)
                sendPapirSykmeldingManuellOppgave(
                    manuellOppgaveService,
                    kafkaProducers.kafkaRecievedSykmeldingProducer,
                    kafkaProducers.kafkaSyfoserviceProducers,
                    oppgaveClient,
                    kuhrsarClient,
                    dokArkivClient,
                    regelClient,
                    pdlService,
                    sykmelderService,
                    authorizationService
                )
                sykmelderApi(sykmelderService)
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
