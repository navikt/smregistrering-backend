package no.nav.syfo.client

import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.azuread.v2.AzureAdV2TokenResponse
import no.nav.syfo.objectMapper
import no.nav.syfo.testutil.HttpClientTest
import no.nav.syfo.testutil.ResponseData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IstilgangskontrollClientTest {

    private val pasientFnr = "123145"

    private val httpClient = HttpClientTest()
    private val env =
        mockk<Environment>() {
            coEvery { azureAppClientId } returns "clientId"
            coEvery { azureAppClientSecret } returns "secret"
            coEvery { azureTokenEndpoint } returns "http://obo"
            coEvery { istilgangskontrollClientUrl } returns "http://istilgangskontroll"
            coEvery { istilgangskontrollScope } returns "scope"
        }
    private val azureAdV2Client = spyk(AzureAdV2Client(env, httpClient.httpClient))

    private val istilgangskontrollCache =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build<Map<String, String>, Tilgang>()

    private val istilgangskontrollClient =
        IstilgangskontrollClient(
            environment = env,
            azureAdV2Client = azureAdV2Client,
            httpClient = httpClient.httpClient,
            istilgangskontrollCache = istilgangskontrollCache,
        )

    @BeforeEach
    internal fun beforeEachTest() {
        clearAllMocks()
        istilgangskontrollCache.invalidateAll()
    }

    @Test
    internal fun `Skal returnere harTilgang til true`() {
        httpClient.responseDataOboToken =
            ResponseData(
                HttpStatusCode.OK,
                objectMapper.writeValueAsString(
                    AzureAdV2TokenResponse("token", 1000000, "token_type"),
                ),
            )
        httpClient.responseData =
            ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true)))
        runBlocking {
            val tilgang = istilgangskontrollClient.hasAccess("sdfsdfsfs", pasientFnr)
            assertEquals(true, tilgang.erGodkjent)
        }
    }

    @Test
    internal fun `Skal returnere harTilgang false hvis istilgangskontroll svarer med feilmelding`() {
        httpClient.responseDataOboToken =
            ResponseData(
                HttpStatusCode.OK,
                objectMapper.writeValueAsString(
                    AzureAdV2TokenResponse("token", 1000000, "token_type")
                )
            )
        httpClient.responseData =
            ResponseData(
                HttpStatusCode.InternalServerError,
                objectMapper.writeValueAsString(Tilgang(false))
            )
        runBlocking {
            val tilgang = istilgangskontrollClient.hasAccess("sdfsdfsfs", pasientFnr)
            assertEquals(false, tilgang.erGodkjent)
        }
    }

    @Test
    internal fun `Henter fra cache hvis kallet er cachet`() {
        httpClient.responseDataOboToken =
            ResponseData(
                HttpStatusCode.OK,
                objectMapper.writeValueAsString(
                    AzureAdV2TokenResponse("token", 1000000, "token_type")
                )
            )
        httpClient.responseData =
            ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true)))
        runBlocking {
            istilgangskontrollClient.hasAccess("sdfsdfsfs", pasientFnr)
            istilgangskontrollClient.hasAccess("sdfsdfsfs", pasientFnr)
        }

        coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("sdfsdfsfs", "scope") }
    }

    @Test
    internal fun `Henter ikke fra cache hvis samme accesstoken men ulikt fnr`() {
        httpClient.responseDataOboToken =
            ResponseData(
                HttpStatusCode.OK,
                objectMapper.writeValueAsString(
                    AzureAdV2TokenResponse("token", 1000000, "token_type")
                )
            )
        httpClient.responseData =
            ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true)))
        runBlocking {
            istilgangskontrollClient.hasAccess("sdfsdfsfs", pasientFnr)
            istilgangskontrollClient.hasAccess("sdfsdfsfs", "987654")
        }

        coVerify(exactly = 2) { azureAdV2Client.getOnBehalfOfToken("sdfsdfsfs", "scope") }
    }

    @Test
    internal fun `Henter ikke fra cache hvis samme fnr men ulikt accesstoken`() {
        httpClient.responseDataOboToken =
            ResponseData(
                HttpStatusCode.OK,
                objectMapper.writeValueAsString(
                    AzureAdV2TokenResponse("token", 1000000, "token_type")
                )
            )
        httpClient.responseData =
            ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true)))
        runBlocking {
            istilgangskontrollClient.hasAccess("sdfsdfsfs", pasientFnr)
            istilgangskontrollClient.hasAccess("xxxxxxxxx", pasientFnr)
        }

        coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("sdfsdfsfs", "scope") }
        coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("xxxxxxxxx", "scope") }
    }
}
