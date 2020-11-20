package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
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
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldEqual
import org.junit.Test
import org.junit.jupiter.api.BeforeEach

class SyfoTilgangsKontrollClientTest {

    companion object {
        private val mockHttpServerPort = ServerSocket(0).use { it.localPort }
        private val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
        private val pasientFnr = "123145"
        private val veilederident = "Z990099"
        private val mockServer = embeddedServer(Netty, mockHttpServerPort) {
            install(ContentNegotiation) {
                jackson {}
            }
            routing {
                get("/api/tilgang/navident/bruker/$pasientFnr") {
                    when {
                        call.request.headers["Authorization"] == "Bearer token" -> call.respond(
                            Tilgang(
                                harTilgang = true,
                                begrunnelse = null
                            )
                        )
                        else -> call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt")
                    }
                }
                get("/api/veilederinfo/ident") {
                    when {
                        call.request.headers["Authorization"] == "Bearer token" -> call.respond(Veileder(veilederident))
                        else -> call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt")
                    }
                }
            }
        }.start()

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
    }

    private val accessTokenClient = mockk<AccessTokenClient>()
    private val syfoTilgangskontrollCache: Cache<Map<String, String>, Tilgang> = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .maximumSize(100)
        .build<Map<String, String>, Tilgang>()

    private val veilederCache: Cache<String, Veileder> = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .maximumSize(100)
        .build<String, Veileder>()

    private val syfoTilgangsKontrollClient = SyfoTilgangsKontrollClient(
        mockHttpServerUrl,
        accessTokenClient,
        "scope",
        httpClient,
        syfoTilgangskontrollCache,
        veilederCache
    )

    @BeforeEach
    internal fun beforeEachTest() {
        clearAllMocks()
        syfoTilgangskontrollCache.invalidateAll()
        veilederCache.invalidateAll()
    }

    @Test
    internal fun `Skal returnere harTilgang til true`() {
        coEvery { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) } returns "token"
        runBlocking {
            val tilgang = syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
            tilgang?.harTilgang shouldEqual true
        }
    }

    @Test
    internal fun `Skal returnere harTilgang false hvis syfotilgangskontroll svarer med feilmelding`() {
        coEvery { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) } returns "annetToken"
        runBlocking {
            val tilgang = syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
            tilgang?.harTilgang shouldEqual false
        }
    }

    @Test
    internal fun `Henter fra cache hvis kallet er cachet`() {
        coEvery { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) } returns "token"
        runBlocking {
            syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
            syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
        }

        coVerify(exactly = 1) { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) }
    }

    @Test
    internal fun `Henter ikke fra cache hvis samme accesstoken men ulikt fnr`() {
        coEvery { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) } returns "token"
        runBlocking {
            syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
            syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", "987654")
        }

        coVerify(exactly = 2) { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) }
    }

    @Test
    internal fun `Henter ikke fra cache hvis samme fnr men ulikt accesstoken`() {
        coEvery { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) } returns "token"
        runBlocking {
            syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
            syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("xxxxxxxxx", pasientFnr)
        }

        coVerify(exactly = 2) { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) }
    }

    @Test
    internal fun `Skal returnere veileder`() {
        coEvery { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) } returns "token"
        runBlocking {
            val veileder = syfoTilgangsKontrollClient.hentVeilderIdentViaAzure("sdfsdfsfs")
            veileder?.veilederIdent shouldEqual veilederident
        }
    }

    @Test
    internal fun `Skal returnere null for veileder hvis syfotilgangskontroll svarer med feilmelding`() {
        coEvery { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) } returns "annetToken"
        runBlocking {
            val veileder = syfoTilgangsKontrollClient.hentVeilderIdentViaAzure("sdfsdfsfs")
            veileder?.veilederIdent shouldEqual null
        }
    }

    @Test
    internal fun `Henter veileder fra cache hvis kallet er cachet`() {
        coEvery { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) } returns "token"
        runBlocking {
            syfoTilgangsKontrollClient.hentVeilderIdentViaAzure("sdfsdfsfs")
            syfoTilgangsKontrollClient.hentVeilderIdentViaAzure("sdfsdfsfs")
        }

        coVerify(exactly = 1) { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) }
    }

    @Test
    internal fun `Henter ikke veileder fra cache hvis ulikt accesstoken`() {
        coEvery { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) } returns "token"
        runBlocking {
            syfoTilgangsKontrollClient.hentVeilderIdentViaAzure("sdfsdfsfs")
            syfoTilgangsKontrollClient.hentVeilderIdentViaAzure("xxxxxxxxx")
        }

        coVerify(exactly = 2) { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) }
    }
}
