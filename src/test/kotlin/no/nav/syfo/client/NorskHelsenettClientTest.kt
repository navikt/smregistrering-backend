package no.nav.syfo.client

import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelder.exception.SykmelderNotFoundException
import no.nav.syfo.testutil.HttpClientTest
import no.nav.syfo.testutil.ResponseData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class NorskHelsenettClientTest {
    private val azureAdV2Client = mockk<AzureAdV2Client>()
    private val httpClient = HttpClientTest()
    private val norskHelsenettClient = NorskHelsenettClient(
        "https://syfohelsenettproxy",
        azureAdV2Client,
        "resource",
        httpClient.httpClient
    )

    @BeforeEach
    internal fun beforeTest() {
        coEvery { azureAdV2Client.getAccessToken(any()) } returns "token"
    }

    @Test
    internal fun `Henter behandler fra HPR`() {
        httpClient.responseData = ResponseData(
            HttpStatusCode.OK,
            objectMapper.writeValueAsString(
                Behandler(emptyList(), "12345678910", "Fornavn", null, "Etternavn")
            )
        )
        runBlocking {
            val behandler = norskHelsenettClient.finnBehandler("hpr", "callid")

            assertEquals("12345678910", behandler.fnr)
        }
    }

    @Test
    internal fun `Kaster SykmelderNotFoundException hvis behandler ikke finnes i HPR`() {
        httpClient.responseData = ResponseData(HttpStatusCode.NotFound, "Behandler ikke funnet")
        runBlocking {
            assertFailsWith<SykmelderNotFoundException> {
                norskHelsenettClient.finnBehandler("hpr", "callid")
            }
        }
    }
}
