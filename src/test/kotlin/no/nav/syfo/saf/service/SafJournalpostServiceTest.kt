package no.nav.syfo.saf.service

import io.mockk.coEvery
import io.mockk.mockk
import java.lang.RuntimeException
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.graphql.model.GraphQLResponse
import no.nav.syfo.saf.SafJournalpostClient
import no.nav.syfo.saf.model.Journalpost
import no.nav.syfo.saf.model.JournalpostResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SafJournalpostServiceTest {

    private val safJournalpostClient = mockk<SafJournalpostClient>()
    private val env =
        mockk<Environment>() {
            coEvery { azureAppClientId } returns "clientId"
            coEvery { azureAppClientSecret } returns "secret"
            coEvery { azureTokenEndpoint } returns "http://obo"
            coEvery { safScope } returns "scope"
        }
    private val azureAdV2Client = mockk<AzureAdV2Client>()
    private val safJournalpostService =
        SafJournalpostService(env, azureAdV2Client, safJournalpostClient)

    @Test
    fun erJournalfoert_graphQLResponseNull() {
        coEvery { safJournalpostClient.getJournalpostMetadata(any(), any()) } returns null
        coEvery { azureAdV2Client.getOnBehalfOfToken(any(), any()) } returns "token"

        assertFailsWith<RuntimeException> {
            runBlocking { safJournalpostService.erJournalfoert("foo", "bar") }
        }
    }

    @Test
    fun erJournalfoert_journalStatusNull() {
        coEvery { safJournalpostClient.getJournalpostMetadata(any(), any()) } returns
            GraphQLResponse(
                JournalpostResponse(journalpost = Journalpost(null, dokumenter = emptyList())),
                errors = null
            )
        coEvery { azureAdV2Client.getOnBehalfOfToken(any(), any()) } returns "token"

        assertFailsWith<RuntimeException> {
            runBlocking { safJournalpostService.erJournalfoert("foo", "bar") }
        }
    }

    @Test
    fun erJournalfoert_TRUE() {
        coEvery { safJournalpostClient.getJournalpostMetadata(any(), any()) } returns
            GraphQLResponse(
                JournalpostResponse(
                    journalpost = Journalpost("JOURNALFOERT", dokumenter = emptyList())
                ),
                errors = null
            )
        coEvery { azureAdV2Client.getOnBehalfOfToken(any(), any()) } returns "token"

        runBlocking { assertEquals(true, safJournalpostService.erJournalfoert("foo", "bar")) }
    }

    @Test
    fun erJournalfoert_FALSE() {
        coEvery { safJournalpostClient.getJournalpostMetadata(any(), any()) } returns
            GraphQLResponse(
                JournalpostResponse(journalpost = Journalpost("MOTTATT", dokumenter = emptyList())),
                errors = null
            )
        coEvery { azureAdV2Client.getOnBehalfOfToken(any(), any()) } returns "token"

        runBlocking { assertEquals(false, safJournalpostService.erJournalfoert("foo", "bar")) }
    }
}
