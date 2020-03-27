package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import java.nio.file.Paths
import java.time.LocalDateTime
import no.nav.syfo.VaultSecrets
import no.nav.syfo.aksessering.api.hentPapirSykmeldingManuellOppgave
import no.nav.syfo.application.setupAuth
import no.nav.syfo.log
import no.nav.syfo.model.ManuellOppgaveDTO
import no.nav.syfo.model.PapirSmRegistering
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.junit.Test

@KtorExperimentalAPI
internal class AuthenticateTest {

    private val path = "src/test/resources/jwkset.json"
    private val uri = Paths.get(path).toUri().toURL()
    private val jwkProvider = JwkProviderBuilder(uri).build()
    private val database = TestDB()
    private val manuellOppgaveService = ManuellOppgaveService(database)

    @Test
    internal fun `Aksepterer gyldig JWT med riktig audience`() {
        with(TestApplicationEngine()) {
            start()

            val oppgaveid = 308076319

            val loggingMeta = LoggingMeta(
                mottakId = "1344444",
                journalpostId = "134",
                dokumentInfoId = "131313",
                msgId = "1344444",
                sykmeldingId = "1344444"
            )

            val manuellOppgave = PapirSmRegistering(
                journalpostId = "134",
                fnr = "41424",
                aktorId = "1314",
                dokumentInfoId = "131313",
                datoOpprettet = LocalDateTime.now(),
                sykmeldingId = "1344444"
            )

            database.opprettManuellOppgave(manuellOppgave, oppgaveid, ByteArray(1))

            application.setupAuth(
                VaultSecrets(
                    serviceuserUsername = "username",
                    serviceuserPassword = "password",
                    oidcWellKnownUri = "https://sts.issuer.net/myid",
                    smregistreringBackendClientId = "clientId"
                ), jwkProvider, "https://sts.issuer.net/myid"
            )
            application.routing {
                authenticate("jwt") {
                    hentPapirSykmeldingManuellOppgave(manuellOppgaveService)
                }
            }

            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            application.install(StatusPages) {
                exception<Throwable> { cause ->
                    call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                    log.error("Caught exception", cause)
                    throw cause
                }
            }

            with(handleRequest(HttpMethod.Get, "/api/v1/hentPapirSykmeldingManuellOppgave/?oppgaveid=$oppgaveid") {
                addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
            }) {
                response.status() shouldEqual HttpStatusCode.OK
                objectMapper.readValue<List<ManuellOppgaveDTO>>(response.content!!)
                    .first().oppgaveid shouldEqual oppgaveid
            }
        }
    }

    @Test
    internal fun `Gyldig JWT med feil audience gir Unauthorized`() {
        with(TestApplicationEngine()) {
            start()

            val oppgaveid = 308076319

            val loggingMeta = LoggingMeta(
                mottakId = "1344444",
                journalpostId = "134",
                dokumentInfoId = "131313",
                msgId = "1344444",
                sykmeldingId = "1344444"
            )

            val manuellOppgave = PapirSmRegistering(
                journalpostId = "134",
                fnr = "41424",
                aktorId = "1314",
                dokumentInfoId = "131313",
                datoOpprettet = LocalDateTime.now(),
                sykmeldingId = "1344444"
            )

            database.opprettManuellOppgave(manuellOppgave, oppgaveid, ByteArray(1))

            application.setupAuth(
                VaultSecrets(
                    serviceuserUsername = "username",
                    serviceuserPassword = "password",
                    oidcWellKnownUri = "https://sts.issuer.net/myid",
                    smregistreringBackendClientId = "clientId"
                ), jwkProvider, "https://sts.issuer.net/myid"
            )
            application.routing {
                authenticate("jwt") {
                    hentPapirSykmeldingManuellOppgave(manuellOppgaveService)
                }
            }

            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            application.install(StatusPages) {
                exception<Throwable> { cause ->
                    call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                    log.error("Caught exception", cause)
                    throw cause
                }
            }

            with(handleRequest(HttpMethod.Get, "/api/v1/hentPapirSykmeldingManuellOppgave/?oppgaveid=$oppgaveid") {
                addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "annenClientId")}")
            }) {
                response.status() shouldEqual HttpStatusCode.Unauthorized
                response.content shouldEqual null
            }
        }
    }
}
