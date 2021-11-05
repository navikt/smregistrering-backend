package no.nav.syfo.saf.service

import io.ktor.util.InternalAPI
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.azuread.v2.AzureAdV2Token
import no.nav.syfo.graphql.model.GraphQLResponse
import no.nav.syfo.saf.SafJournalpostClient
import no.nav.syfo.saf.model.Journalpost
import no.nav.syfo.saf.model.JournalpostResponse
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import java.lang.RuntimeException
import java.time.OffsetDateTime
import kotlin.test.assertFailsWith

internal class SafJournalpostServiceTest {

    private val safJournalpostClient = mockk<SafJournalpostClient>()
    private val env = mockk<Environment>() {
        coEvery { azureAppClientId } returns "clientId"
        coEvery { azureAppClientSecret } returns "secret"
        coEvery { azureTokenEndpoint } returns "http://obo"
        coEvery { safScope } returns "scope"
    }
    private val azureAdV2Client = mockk<AzureAdV2Client>()
    private val safJournalpostService = SafJournalpostService(env, azureAdV2Client, safJournalpostClient)

    @InternalAPI
    @Test
    fun erJournalfoert_graphQLResponseNull() {
        coEvery { safJournalpostClient.getJournalpostMetadata(any(), any()) } returns null
        coEvery { azureAdV2Client.getOnBehalfOfToken(any(), any()) } returns AzureAdV2Token(
            "token",
            OffsetDateTime.now().plusHours(1)
        )

        assertFailsWith<RuntimeException> {
            runBlocking { safJournalpostService.erJournalfoert("foo", "bar") }
        }
    }

    @InternalAPI
    @Test
    fun erJournalfoert_journalStatusNull() {
        coEvery { safJournalpostClient.getJournalpostMetadata(any(), any()) } returns
            GraphQLResponse(JournalpostResponse(journalpost = Journalpost(null)), errors = null)
        coEvery { azureAdV2Client.getOnBehalfOfToken(any(), any()) } returns AzureAdV2Token(
            "token",
            OffsetDateTime.now().plusHours(1)
        )

        assertFailsWith<RuntimeException> {
            runBlocking { safJournalpostService.erJournalfoert("foo", "bar") }
        }
    }

    @InternalAPI
    @Test
    fun erJournalfoert_TRUE() {
        coEvery { safJournalpostClient.getJournalpostMetadata(any(), any()) } returns
            GraphQLResponse(JournalpostResponse(journalpost = Journalpost("JOURNALFOERT")), errors = null)
        coEvery { azureAdV2Client.getOnBehalfOfToken(any(), any()) } returns AzureAdV2Token(
            "token",
            OffsetDateTime.now().plusHours(1)
        )

        runBlocking { safJournalpostService.erJournalfoert("foo", "bar") } shouldBeEqualTo true
    }

    @InternalAPI
    @Test
    fun erJournalfoert_FALSE() {
        coEvery { safJournalpostClient.getJournalpostMetadata(any(), any()) } returns
            GraphQLResponse(JournalpostResponse(journalpost = Journalpost("MOTTATT")), errors = null)
        coEvery { azureAdV2Client.getOnBehalfOfToken(any(), any()) } returns AzureAdV2Token(
            "token",
            OffsetDateTime.now().plusHours(1)
        )

        runBlocking { safJournalpostService.erJournalfoert("foo", "bar") } shouldBeEqualTo false
    }
}
