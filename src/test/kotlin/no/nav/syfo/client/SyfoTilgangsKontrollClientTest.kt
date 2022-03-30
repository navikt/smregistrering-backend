package no.nav.syfo.client

import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.azuread.v2.AzureAdV2TokenResponse
import no.nav.syfo.objectMapper
import no.nav.syfo.testutil.HttpClientTest
import no.nav.syfo.testutil.ResponseData
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.TimeUnit

class SyfoTilgangsKontrollClientTest {

    private val pasientFnr = "123145"

    private val httpClient = HttpClientTest()
    private val env = mockk<Environment>() {
        coEvery { azureAppClientId } returns "clientId"
        coEvery { azureAppClientSecret } returns "secret"
        coEvery { azureTokenEndpoint } returns "http://obo"
        coEvery { syfoTilgangsKontrollClientUrl } returns "http://syfotilgangskontroll"
        coEvery { syfoTilgangsKontrollScope } returns "scope"
    }
    private val azureAdV2Client = spyk(AzureAdV2Client(env, httpClient.httpClient))

    private val syfoTilgangskontrollCache = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .maximumSize(100)
        .build<Map<String, String>, Tilgang>()

    private val syfoTilgangsKontrollClient = SyfoTilgangsKontrollClient(
        environment = env,
        azureAdV2Client = azureAdV2Client,
        httpClient = httpClient.httpClient,
        syfoTilgangskontrollCache = syfoTilgangskontrollCache
    )

    @BeforeEach
    internal fun beforeEachTest() {
        clearAllMocks()
        syfoTilgangskontrollCache.invalidateAll()
    }

    @Test
    internal fun `Skal returnere harTilgang til true`() {
        httpClient.responseDataOboToken = ResponseData(
            HttpStatusCode.OK,
            objectMapper.writeValueAsString(
                AzureAdV2TokenResponse("token", 1000000, "token_type")
            )
        )
        httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true)))
        runBlocking {
            val tilgang = syfoTilgangsKontrollClient.hasAccess("sdfsdfsfs", pasientFnr)
            tilgang.harTilgang shouldBeEqualTo true
        }
    }

    @Test
    internal fun `Skal returnere harTilgang false hvis syfotilgangskontroll svarer med feilmelding`() {
        httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
        httpClient.responseData = ResponseData(HttpStatusCode.InternalServerError, objectMapper.writeValueAsString(Tilgang(false)))
        runBlocking {
            val tilgang = syfoTilgangsKontrollClient.hasAccess("sdfsdfsfs", pasientFnr)
            tilgang.harTilgang shouldBeEqualTo false
        }
    }

    @Test
    internal fun `Henter fra cache hvis kallet er cachet`() {
        httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
        httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true)))
        runBlocking {
            syfoTilgangsKontrollClient.hasAccess("sdfsdfsfs", pasientFnr)
            syfoTilgangsKontrollClient.hasAccess("sdfsdfsfs", pasientFnr)
        }

        coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("sdfsdfsfs", "scope") }
    }

    @Test
    internal fun `Henter ikke fra cache hvis samme accesstoken men ulikt fnr`() {
        httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
        httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true)))
        runBlocking {
            syfoTilgangsKontrollClient.hasAccess("sdfsdfsfs", pasientFnr)
            syfoTilgangsKontrollClient.hasAccess("sdfsdfsfs", "987654")
        }

        coVerify(exactly = 2) { azureAdV2Client.getOnBehalfOfToken("sdfsdfsfs", "scope") }
    }

    @Test
    internal fun `Henter ikke fra cache hvis samme fnr men ulikt accesstoken`() {
        httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
        httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true)))
        runBlocking {
            syfoTilgangsKontrollClient.hasAccess("sdfsdfsfs", pasientFnr)
            syfoTilgangsKontrollClient.hasAccess("xxxxxxxxx", pasientFnr)
        }

        coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("sdfsdfsfs", "scope") }
        coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("xxxxxxxxx", "scope") }
    }
}
