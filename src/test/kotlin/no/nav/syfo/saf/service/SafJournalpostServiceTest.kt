package no.nav.syfo.saf.service

import io.ktor.client.tests.utils.assertFailsWith
import io.ktor.util.InternalAPI
import io.mockk.coEvery
import io.mockk.mockk
import java.lang.RuntimeException
import kotlinx.coroutines.runBlocking
import no.nav.syfo.graphql.model.GraphQLResponse
import no.nav.syfo.saf.SafJournalpostClient
import no.nav.syfo.saf.model.Journalpost
import no.nav.syfo.saf.model.JournalpostResponse
import org.amshove.kluent.shouldEqual
import org.junit.Test

internal class SafJournalpostServiceTest {

    val safJournalpostClient = mockk<SafJournalpostClient>()
    val safJournalpostService = SafJournalpostService(safJournalpostClient)

    @InternalAPI
    @Test
    fun erJournalfoert_graphQLResponseNull() {
        coEvery { safJournalpostClient.getJournalpostMetadata(any(), any()) } returns null

        assertFailsWith<RuntimeException> {
            runBlocking { safJournalpostService.erJournalfoert("foo", "bar") }
        }
    }

    @InternalAPI
    @Test
    fun erJournalfoert_journalStatusNull() {
        coEvery { safJournalpostClient.getJournalpostMetadata(any(), any()) } returns
                GraphQLResponse(JournalpostResponse(journalpost = Journalpost(null)), errors = null)

        assertFailsWith<RuntimeException> {
            runBlocking { safJournalpostService.erJournalfoert("foo", "bar") }
        }
    }

    @InternalAPI
    @Test
    fun erJournalfoert_TRUE() {
        coEvery { safJournalpostClient.getJournalpostMetadata(any(), any()) } returns
                GraphQLResponse(JournalpostResponse(journalpost = Journalpost("JOURNALFOERT")), errors = null)

        runBlocking { safJournalpostService.erJournalfoert("foo", "bar") } shouldEqual true
    }

    @InternalAPI
    @Test
    fun erJournalfoert_FALSE() {
        coEvery { safJournalpostClient.getJournalpostMetadata(any(), any()) } returns
                GraphQLResponse(JournalpostResponse(journalpost = Journalpost("MOTTATT")), errors = null)

        runBlocking { safJournalpostService.erJournalfoert("foo", "bar") } shouldEqual false
    }
}
