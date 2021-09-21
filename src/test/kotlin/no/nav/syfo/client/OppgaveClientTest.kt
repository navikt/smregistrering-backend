package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.put
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import java.net.ServerSocket
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import no.nav.syfo.model.Oppgave
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.junit.After
import org.junit.Before
import org.junit.Test

@KtorExperimentalAPI
class OppgaveClientTest {
    private val oppgaveId = 1
    private val oppgaveIdFerdigstilt = 2
    private val oppgaveIdNy = 3
    private val stsOidcClientMock = mockk<StsOidcClient>()
    private val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
    private val loggingMetadata = LoggingMeta("", "", "", "", "")

    private val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    private val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    private val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        routing {
            get("/oppgave/$oppgaveId") {
                call.respond(HttpStatusCode.OK, Oppgave(
                    id = oppgaveId, versjon = 1, tildeltEnhetsnr = "9999", opprettetAvEnhetsnr = "9999", aktoerId = "aktoerId", journalpostId = "123", behandlesAvApplikasjon = null, saksreferanse = "1",
                    tilordnetRessurs = null, beskrivelse = "beskrivelse", tema = "SYM", oppgavetype = "JFR", behandlingstype = null, aktivDato = LocalDate.now().minusWeeks(1), fristFerdigstillelse = null,
                    prioritet = "HOY", status = "AAPNET", mappeId = null
                ))
            }
            get("/oppgave/$oppgaveIdFerdigstilt") {
                call.respond(HttpStatusCode.OK, Oppgave(
                    id = oppgaveIdFerdigstilt, versjon = 1, tildeltEnhetsnr = "9999", opprettetAvEnhetsnr = "9999", aktoerId = "aktoerId", journalpostId = "123", behandlesAvApplikasjon = null, saksreferanse = "1",
                    tilordnetRessurs = null, beskrivelse = "beskrivelse", tema = "SYM", oppgavetype = "JFR", behandlingstype = null, aktivDato = LocalDate.now().minusWeeks(1), fristFerdigstillelse = null,
                    prioritet = "HOY", status = "FERDIGSTILT", mappeId = null
                ))
            }
            put("/oppgave/$oppgaveId") {
                call.respond(HttpStatusCode.OK, Oppgave(
                    id = oppgaveId, versjon = 1, tildeltEnhetsnr = "9999", opprettetAvEnhetsnr = "9999", aktoerId = "aktoerId", journalpostId = "123", behandlesAvApplikasjon = null, saksreferanse = "1",
                    tilordnetRessurs = null, beskrivelse = "beskrivelse", tema = "SYM", oppgavetype = "JFR", behandlingstype = null, aktivDato = LocalDate.now().minusWeeks(1), fristFerdigstillelse = null,
                    prioritet = "HOY", status = "AAPNET", mappeId = null
                ))
            }
            post("/oppgave") {
                call.respond(HttpStatusCode.Created, Oppgave(
                    id = oppgaveIdNy, versjon = 1, tildeltEnhetsnr = "9999", opprettetAvEnhetsnr = "9999", aktoerId = "aktoerId", journalpostId = "123", behandlesAvApplikasjon = null, saksreferanse = "1",
                    tilordnetRessurs = null, beskrivelse = "beskrivelse", tema = "SYM", oppgavetype = "JFR", behandlingstype = null, aktivDato = LocalDate.now().minusWeeks(1), fristFerdigstillelse = null,
                    prioritet = "HOY", status = "FERDIGSTILT", mappeId = null
                ))
            }
        }
    }.start()

    private val oppgaveClient = OppgaveClient("$mockHttpServerUrl/oppgave", stsOidcClientMock, httpClient)

    @After
    fun after() {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    @Before
    fun beforeEachTest() {
        coEvery { stsOidcClientMock.oidcToken() } returns OidcToken("token", "type", 300L)
    }

    @Test
    fun patchManuellOppgaveOppdatererApenOppgave() {
        runBlocking {
            val patchetOppgave = oppgaveClient.patchManuellOppgave(oppgaveId, "msgId")

            patchetOppgave.id shouldEqual oppgaveId
        }
    }

    @Test
    fun patchManuellOppgaveOppretterNyOppgaveHvisFerdigstilt() {
        runBlocking {
            val patchetOppgave = oppgaveClient.patchManuellOppgave(oppgaveIdFerdigstilt, "msgId")

            patchetOppgave.id shouldEqual oppgaveIdNy
        }
    }
}
